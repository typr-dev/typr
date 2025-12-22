#!/usr/bin/env python3
"""
SQL file analyzer using sqlglot.
Takes schema and SQL files as JSON input, returns analysis as JSON output.
Supports both PostgreSQL and MariaDB/MySQL dialects.

Input JSON schema:
{
  "dialect": "postgres" | "mysql",
  "schema": {
    "schema_name.table_name": {
      "column_name": {
        "type": "varchar(255)",
        "nullable": true,
        "primary_key": false
      }
    }
  },
  "files": [
    {"path": "path/to/file.sql", "content": "SELECT ..."}
  ]
}

Output JSON schema:
{
  "results": [
    {
      "path": "path/to/file.sql",
      "success": true,
      "query_type": "SELECT",
      "tables": [...],
      "columns": [...],
      "parameters": [...]
    }
  ]
}
"""

import json
import sys
import time
from dataclasses import dataclass, field, asdict
from typing import Optional, Any
import re
from enum import Enum
import multiprocessing
import os

import sqlglot
from sqlglot import exp
from sqlglot.optimizer.qualify import qualify
from sqlglot.optimizer.annotate_types import annotate_types
from sqlglot.lineage import lineage, Node as LineageNode
from sqlglot.schema import MappingSchema


@dataclass
class ColumnOrigin:
    """A single origin for a column (table.column reference, literal, or function)"""
    origin_type: str  # "column", "literal", "function", "expression"
    table: Optional[str] = None
    column: Optional[str] = None
    value: Optional[str] = None  # for literals
    function_name: Optional[str] = None  # for functions
    expression: Optional[str] = None  # SQL expression text


@dataclass
class ColumnLineage:
    """Full lineage for a result column"""
    name: str
    alias: Optional[str]
    inferred_type: Optional[str]
    nullable_from_join: bool
    nullable_in_schema: bool
    is_expression: bool
    # Direct source (first level)
    source_table: Optional[str]
    source_column: Optional[str]
    source_type: Optional[str]  # Type from schema
    source_primary_key: bool
    # Full lineage trace
    origins: list[ColumnOrigin] = field(default_factory=list)


@dataclass
class ParameterInfo:
    """Information about a query parameter"""
    name: str
    position: int
    nullable_hint: bool
    inferred_type: Optional[str]
    source_table: Optional[str]
    source_column: Optional[str]
    context: Optional[str]  # EQ, GT, IN, LIKE, BETWEEN, IS NULL, etc.


class TokenType(Enum):
    """Type of SQL token"""
    STRING_LITERAL = "string_literal"
    COMMENT = "comment"
    CODE = "code"


@dataclass
class Token:
    """A token in SQL text"""
    token_type: TokenType
    content: str


def tokenize_sql(sql: str) -> list[Token]:
    """
    Split SQL into tokens: string literals, comments, and code.
    This allows parameter extraction to only work on code parts.

    Returns a list of tokens that can be joined to reconstruct the original SQL.
    """
    tokens = []
    i = 0
    n = len(sql)

    while i < n:
        # Check for line comment (-- to end of line)
        if i < n - 1 and sql[i:i+2] == '--':
            start = i
            i += 2
            while i < n and sql[i] != '\n':
                i += 1
            if i < n:  # Include the newline
                i += 1
            tokens.append(Token(TokenType.COMMENT, sql[start:i]))
            continue

        # Check for block comment (/* ... */)
        if i < n - 1 and sql[i:i+2] == '/*':
            start = i
            i += 2
            while i < n - 1:
                if sql[i:i+2] == '*/':
                    i += 2
                    break
                i += 1
            else:
                # Unterminated block comment - consume to end
                i = n
            tokens.append(Token(TokenType.COMMENT, sql[start:i]))
            continue

        # Check for string literal (single quotes with escaping)
        if sql[i] == "'":
            start = i
            i += 1
            while i < n:
                if sql[i] == "'":
                    # Check if it's escaped (doubled single quote)
                    if i + 1 < n and sql[i + 1] == "'":
                        # Escaped quote, skip both
                        i += 2
                    else:
                        # End of string
                        i += 1
                        break
                elif sql[i] == '\\' and i + 1 < n:
                    # Backslash escape (some SQL dialects support this)
                    i += 2
                else:
                    i += 1
            tokens.append(Token(TokenType.STRING_LITERAL, sql[start:i]))
            continue

        # Otherwise, accumulate code until we hit a comment or string
        start = i
        while i < n and sql[i] not in ("'", "-", "/"):
            i += 1

        # Check if we stopped at a potential comment
        if i < n and sql[i] in ("-", "/"):
            # Peek ahead to see if it's actually a comment
            if (sql[i] == "-" and i + 1 < n and sql[i + 1] == "-") or \
               (sql[i] == "/" and i + 1 < n and sql[i + 1] == "*"):
                # It's a comment, don't include this character
                pass
            else:
                # Not a comment, include the character
                i += 1

        if i > start:
            tokens.append(Token(TokenType.CODE, sql[start:i]))

    return tokens


@dataclass
class TableRef:
    """A table reference in the query"""
    name: str
    alias: Optional[str]
    schema_name: Optional[str]
    join_type: Optional[str]  # LEFT, RIGHT, INNER, CROSS, FULL, None for first table


@dataclass
class SqlFileResult:
    """Analysis result for a single SQL file"""
    path: str
    success: bool
    error: Optional[str] = None
    query_type: Optional[str] = None
    tables: list[TableRef] = field(default_factory=list)
    columns: list[ColumnLineage] = field(default_factory=list)
    parameters: list[ParameterInfo] = field(default_factory=list)


def extract_parameters(sql: str) -> tuple[str, list[ParameterInfo]]:
    """
    Extract named parameters from typo-style SQL.
    Converts :name or :"name!" or :"name?" to ? placeholders.
    Only processes parameters in code sections, not in strings or comments.
    Returns modified SQL and list of parameters.
    """
    tokens = tokenize_sql(sql)
    params = []
    position = 0

    # Pattern for typo parameters: :name, :"name!", :"name?", :name:Type!
    # Use negative lookbehind to NOT match PostgreSQL casts like ::text
    pattern = r'(?<!:):("?)([a-zA-Z_][a-zA-Z0-9_]*)((?::[a-zA-Z_][a-zA-Z0-9_.]*)?[!?]?)(\1)'

    def replace_param(match):
        nonlocal position
        name = match.group(2)
        suffix = match.group(3)

        nullable_hint = suffix.endswith('?')
        position += 1
        params.append(ParameterInfo(
            name=name,
            position=position,
            nullable_hint=nullable_hint,
            inferred_type=None,
            source_table=None,
            source_column=None,
            context=None
        ))
        return '?'

    # Process only CODE tokens, keep strings and comments unchanged
    result_parts = []
    for token in tokens:
        if token.token_type == TokenType.CODE:
            # Apply parameter replacement only to code
            modified_content = re.sub(pattern, replace_param, token.content)
            result_parts.append(modified_content)
        else:
            # Keep strings and comments unchanged
            result_parts.append(token.content)

    modified_sql = ''.join(result_parts)
    return modified_sql, params


def collect_placeholders_in_order(node: exp.Expression, results: list) -> None:
    """Recursively collect placeholders in left-to-right text order."""
    if isinstance(node, exp.Placeholder):
        results.append(node)
    elif isinstance(node, exp.Expression):
        for arg_name in node.arg_types:
            child = node.args.get(arg_name)
            if child is None:
                continue
            if isinstance(child, list):
                for c in child:
                    if isinstance(c, exp.Expression):
                        collect_placeholders_in_order(c, results)
            elif isinstance(child, exp.Expression):
                collect_placeholders_in_order(child, results)


def infer_parameter_types(annotated_ast, alias_to_table: dict, dialect: str) -> list[dict]:
    """
    Walk the annotated AST to find ALL placeholders and infer their types from context.
    """
    results = []

    placeholders = []
    collect_placeholders_in_order(annotated_ast, placeholders)

    for node in placeholders:
        parent = node.parent
        child = node

        # If the immediate parent is a Paren (from parameter syntax like :"param"!),
        # walk up to find the actual operator context
        while parent and isinstance(parent, exp.Paren):
            child = parent
            parent = parent.parent

        info = {
            'type': None,
            'source_table': None,
            'source_column': None,
            'context': None
        }

        if isinstance(parent, exp.Is):
            info['context'] = 'IS NULL'

        elif isinstance(parent, (exp.EQ, exp.NEQ, exp.GT, exp.GTE, exp.LT, exp.LTE)):
            if parent.left == child:
                other = parent.right
            else:
                other = parent.left

            other_type = other.type if hasattr(other, 'type') else None
            info['type'] = str(other_type) if other_type else None
            info['context'] = type(parent).__name__

            if isinstance(other, exp.Column):
                alias = other.table
                # If no alias, try to extract from UPDATE statement context
                if not alias:
                    # Walk up to find the UPDATE node and extract the table name
                    curr = parent
                    while curr and not isinstance(curr, exp.Update):
                        curr = curr.parent
                    if isinstance(curr, exp.Update) and hasattr(curr, 'this'):
                        update_table = curr.this
                        if isinstance(update_table, exp.Table):
                            # Build fully qualified table name
                            if update_table.db:
                                alias = f"{update_table.db}.{update_table.name}"
                            else:
                                alias = update_table.name
                info['source_table'] = alias_to_table.get(alias, alias) if alias else None
                info['source_column'] = other.name

        elif isinstance(parent, exp.DPipe):
            # String concatenation operator ||
            # Look at the other side to infer the type
            if parent.left == child:
                other = parent.right
            else:
                other = parent.left

            other_type = other.type if hasattr(other, 'type') else None
            info['type'] = str(other_type) if other_type else None
            info['context'] = 'DPipe'

            if isinstance(other, exp.Column):
                alias = other.table
                # If no alias, try to extract from UPDATE statement context
                if not alias:
                    curr = parent
                    while curr and not isinstance(curr, exp.Update):
                        curr = curr.parent
                    if isinstance(curr, exp.Update) and hasattr(curr, 'this'):
                        update_table = curr.this
                        if isinstance(update_table, exp.Table):
                            if update_table.db:
                                alias = f"{update_table.db}.{update_table.name}"
                            else:
                                alias = update_table.name
                info['source_table'] = alias_to_table.get(alias, alias) if alias else None
                info['source_column'] = other.name

        elif isinstance(parent, (exp.Like, exp.ILike)):
            this = parent.this
            this_type = this.type if hasattr(this, 'type') else None
            info['type'] = str(this_type) if this_type else None
            info['context'] = type(parent).__name__

            if isinstance(this, exp.Column):
                alias = this.table
                info['source_table'] = alias_to_table.get(alias, alias) if alias else None
                info['source_column'] = this.name

        elif isinstance(parent, exp.In):
            this = parent.this
            this_type = this.type if hasattr(this, 'type') else None
            info['type'] = str(this_type) if this_type else None
            info['context'] = 'IN'

            if isinstance(this, exp.Column):
                alias = this.table
                info['source_table'] = alias_to_table.get(alias, alias) if alias else None
                info['source_column'] = this.name

        elif isinstance(parent, exp.Between):
            this = parent.this
            this_type = this.type if hasattr(this, 'type') else None
            info['type'] = str(this_type) if this_type else None
            info['context'] = 'BETWEEN'

            if isinstance(this, exp.Column):
                alias = this.table
                info['source_table'] = alias_to_table.get(alias, alias) if alias else None
                info['source_column'] = this.name

        elif isinstance(parent, exp.Cast):
            # Parameter is being cast - get the target type
            cast_type = parent.to
            if cast_type:
                info['type'] = cast_type.sql(dialect=dialect)
                info['context'] = 'CAST'

        elif isinstance(parent, (exp.Limit, exp.Offset)):
            # LIMIT and OFFSET always accept integer values
            info['type'] = 'BIGINT'
            info['context'] = type(parent).__name__.upper()

        else:
            info['context'] = type(parent).__name__ if parent else 'unknown'

        results.append(info)

    return results


def trace_column_lineage(sql: str, column_name: str, schema_dict: dict, dialect: str) -> list[ColumnOrigin]:
    """
    Use sqlglot's lineage to trace a column back to all its sources.
    Returns list of origins (table.column refs, literals, functions).
    """
    origins = []
    try:
        node = lineage(column_name, sql, schema=schema_dict, dialect=dialect)

        def walk_lineage(n: LineageNode, visited: set):
            node_id = id(n)
            if node_id in visited:
                return
            visited.add(node_id)

            # Check if this node points to a real table
            if n.downstream:
                for downstream in n.downstream:
                    source = downstream.source

                    if isinstance(source, exp.Table):
                        # Direct table reference
                        table_name = source.name
                        parts = downstream.name.split('.')
                        col_name = parts[-1] if parts else downstream.name
                        origins.append(ColumnOrigin(
                            origin_type="column",
                            table=table_name,
                            column=col_name
                        ))
                    elif isinstance(source, exp.Select):
                        # CTE or subquery - recurse
                        walk_lineage(downstream, visited)

            # Check for literals
            if isinstance(n.expression, exp.Literal):
                origins.append(ColumnOrigin(
                    origin_type="literal",
                    value=str(n.expression.this)
                ))
            # Check for functions
            elif isinstance(n.expression, exp.Func):
                func_name = n.expression.sql_name() if hasattr(n.expression, 'sql_name') else type(n.expression).__name__
                origins.append(ColumnOrigin(
                    origin_type="function",
                    function_name=func_name
                ))

        walk_lineage(node, set())

    except Exception as e:
        # Log but continue - lineage is optional enhancement
        print(f"Warning: lineage failed for column '{column_name}': {e}", file=sys.stderr)

    return origins


def get_direct_column_source(qualified_ast: exp.Expression, column_name: str, schema_dict: dict, dialect: str) -> tuple[Optional[str], Optional[str], bool]:
    """
    Get the direct source table and column for a result column.
    Returns (source_table, source_column, is_expression)

    Args:
        qualified_ast: The qualified AST (from qualify())
        column_name: Column name to trace
        schema_dict: Schema dictionary
        dialect: SQL dialect
    """
    try:
        # Use the qualified AST instead of raw SQL string
        node = lineage(column_name, qualified_ast, schema=schema_dict, dialect=dialect)

        if node.downstream:
            for downstream in node.downstream:
                source = downstream.source

                if isinstance(source, exp.Table):
                    # Build fully qualified table name
                    if source.db:
                        table_name = f"{source.db}.{source.name}"
                    else:
                        table_name = source.name
                    parts = downstream.name.split('.')
                    col_name = parts[-1] if parts else downstream.name

                    # Determine if this is a computed expression vs a direct column reference
                    # by examining node.expression (the SELECT expression), not downstream.expression
                    is_expr = True  # Default to expression
                    if isinstance(node.expression, exp.Alias):
                        inner = node.expression.this
                        # If it's Alias(this=Column(...)), it's a direct column reference
                        # If it's a binary operation (Add, Sub, Mul, Div, etc.) or other expression, it's computed
                        if isinstance(inner, exp.Column):
                            is_expr = False
                        elif isinstance(inner, (exp.Binary, exp.Unary)):
                            # Arithmetic or logical operations are always expressions
                            # This catches cases like (col1 - col2) AS available
                            is_expr = True
                    elif isinstance(node.expression, exp.Column):
                        # Simple Column without Alias is also a direct reference
                        is_expr = False
                    elif isinstance(node.expression, (exp.Binary, exp.Unary)):
                        # Arithmetic or logical operations without alias are expressions
                        is_expr = True

                    return table_name, col_name, is_expr

                if isinstance(source, exp.Select):
                    # When selecting from a subquery, need to trace through to find the ultimate source
                    # Check if this column in the outer SELECT is a simple column reference or an expression
                    # by examining node.expression (the outer SELECT's expression for this column)
                    is_expr = True  # Default to expression
                    if isinstance(node.expression, exp.Alias):
                        inner = node.expression.this
                        # If it's Alias(this=Column(...)), it's selecting a column from the subquery
                        # If it's a binary operation or other expression, it's computed
                        if isinstance(inner, exp.Column):
                            is_expr = False
                        elif isinstance(inner, (exp.Binary, exp.Unary)):
                            # Arithmetic or logical operations are always expressions
                            is_expr = True
                    elif isinstance(node.expression, exp.Column):
                        # Simple Column without Alias is also a direct reference
                        is_expr = False
                    elif isinstance(node.expression, (exp.Binary, exp.Unary)):
                        # Arithmetic or logical operations without alias are expressions
                        is_expr = True

                    if is_expr:
                        # This is an aggregate or computed expression, no typeflow
                        return None, None, True

                    # It's a direct column reference - continue with lineage traversal
                    # The downstream node might have more depth to explore
                    if isinstance(downstream.expression, exp.Alias):
                        inner = downstream.expression.this
                        if isinstance(inner, exp.Column):
                            # Search for deeper sources in the lineage graph
                            # Walk the downstream nodes to find the actual table
                            def find_table_source(lineage_node, visited=None):
                                if visited is None:
                                    visited = set()
                                node_id = id(lineage_node)
                                if node_id in visited:
                                    return None, None
                                visited.add(node_id)

                                if lineage_node.downstream:
                                    for ds in lineage_node.downstream:
                                        if isinstance(ds.source, exp.Table):
                                            # Found a table!
                                            table_name = ds.source.name
                                            if ds.source.db:
                                                table_name = f"{ds.source.db}.{table_name}"
                                            parts = ds.name.split('.')
                                            col_name = parts[-1] if parts else ds.name
                                            if table_name in schema_dict:
                                                return table_name, col_name
                                        elif isinstance(ds.source, exp.Select):
                                            # Recurse deeper
                                            result = find_table_source(ds, visited)
                                            if result[0]:
                                                return result
                                return None, None

                            table_result = find_table_source(downstream)
                            if table_result[0]:
                                return table_result[0], table_result[1], False

        return None, None, False
    except Exception as e:
        # Log but continue - source tracking is optional enhancement
        print(f"Warning: get_direct_column_source failed for '{column_name}': {e}", file=sys.stderr)
        return None, None, False


# Track which types we've already warned about to avoid duplicate warnings
_warned_types = set()

def build_sqlglot_schema(schema: dict, dialect: str) -> tuple[MappingSchema, dict]:
    """
    Build sqlglot MappingSchema and dict from input schema.
    This is expensive, so should be done once and reused.
    """
    sqlglot_schema = MappingSchema(dialect=dialect)
    sqlglot_schema_dict = {}

    for table_name, columns in schema.items():
        table_cols = {}
        table_cols_dict = {}

        for col_name, col_info in columns.items():
            if isinstance(col_info, dict):
                # Preserve the full dict for our lookups (includes nullable, primary_key)
                table_cols_dict[col_name] = col_info

                # Build exp.DataType for sqlglot's inference
                col_type = col_info.get("type", "VARCHAR")
                try:
                    dtype = exp.DataType.build(col_type, dialect=dialect)
                    table_cols[col_name] = dtype
                except Exception as e:
                    # Try spatial types, otherwise use string
                    spatial_types = {
                        "point": exp.DataType.Type.POINT,
                        "linestring": exp.DataType.Type.LINESTRING,
                        "polygon": exp.DataType.Type.POLYGON,
                        "geometry": exp.DataType.Type.GEOMETRY,
                    }
                    lower_type = col_type.lower()
                    if lower_type in spatial_types:
                        table_cols[col_name] = exp.DataType(this=spatial_types[lower_type])
                    else:
                        # Only warn once per unique type to reduce verbosity
                        if col_type not in _warned_types:
                            _warned_types.add(col_type)
                            print(f"Warning: couldn't parse type '{col_type}': {e}", file=sys.stderr)
                        table_cols[col_name] = col_type
            else:
                # Not a dict - just use the value directly
                table_cols[col_name] = col_info
                table_cols_dict[col_name] = col_info

        # Quote table name if it contains special characters (like hyphens)
        # sqlglot expects quoted identifiers for names with special chars
        quoted_table_name = table_name
        if '.' in table_name:
            # Split schema.table and quote each part if needed
            parts = table_name.split('.', 1)
            quoted_parts = []
            for part in parts:
                if re.search(r'[^a-zA-Z0-9_]', part):
                    quoted_parts.append(f'"{part}"')
                else:
                    quoted_parts.append(part)
            quoted_table_name = '.'.join(quoted_parts)
        elif re.search(r'[^a-zA-Z0-9_]', table_name):
            quoted_table_name = f'"{table_name}"'

        try:
            sqlglot_schema.add_table(quoted_table_name, table_cols)
        except Exception as e:
            print(f"Warning: couldn't add table '{table_name}' to schema: {e}", file=sys.stderr)

        # Store the dict version for our lookups (preserves nullable, primary_key)
        sqlglot_schema_dict[table_name] = table_cols_dict

    return sqlglot_schema, sqlglot_schema_dict


def analyze_sql(sql: str, sqlglot_schema: MappingSchema, sqlglot_schema_dict: dict, dialect: str, path: str = "") -> SqlFileResult:
    """Analyze a single SQL query."""
    start_time = time.time()

    # Print which file we're analyzing if it takes time
    if path:
        print(f"Analyzing {path}...", file=sys.stderr)

    # Extract parameters first
    param_start = time.time()
    clean_sql, parameters = extract_parameters(sql)
    param_time = time.time() - param_start
    if param_time > 0.1:
        print(f"  extract_parameters: {param_time*1000:.0f}ms", file=sys.stderr)

    # Parse the SQL
    parse_start = time.time()
    try:
        parsed = sqlglot.parse_one(clean_sql, dialect=dialect)
    except Exception as e:
        return SqlFileResult(
            path="",
            success=False,
            error=f"Parse error: {str(e)}",
            parameters=[asdict(p) for p in parameters]
        )
    parse_time = time.time() - parse_start
    if parse_time > 0.1:
        print(f"  parse: {parse_time*1000:.0f}ms", file=sys.stderr)

    # Determine query type
    if isinstance(parsed, exp.Select):
        query_type = "SELECT"
    elif isinstance(parsed, exp.Update):
        query_type = "UPDATE"
    elif isinstance(parsed, exp.Insert):
        query_type = "INSERT"
    elif isinstance(parsed, exp.Delete):
        query_type = "DELETE"
    else:
        query_type = type(parsed).__name__

    # Qualify and annotate - these may fail on complex SQL
    qualify_start = time.time()
    try:
        qualified = qualify(parsed, schema=sqlglot_schema, dialect=dialect)
    except Exception as e:
        # Log but continue - PostgreSQL views get schema from DB anyway
        print(f"Warning: qualify failed: {e}", file=sys.stderr)
        qualified = parsed
    qualify_time = time.time() - qualify_start
    if qualify_time > 0.1:
        print(f"  qualify: {qualify_time*1000:.0f}ms", file=sys.stderr)

    annotate_start = time.time()
    try:
        annotated = annotate_types(qualified, schema=sqlglot_schema, dialect=dialect)
    except Exception as e:
        # Log but continue
        print(f"Warning: annotate_types failed: {e}", file=sys.stderr)
        annotated = qualified
    annotate_time = time.time() - annotate_start
    if annotate_time > 0.1:
        print(f"  annotate_types: {annotate_time*1000:.0f}ms", file=sys.stderr)

    # Extract table references
    tables_start = time.time()
    tables = []
    tables_by_alias: dict[str, TableRef] = {}
    nullable_aliases: set[str] = set()

    for table in qualified.find_all(exp.Table):
        table_name = table.name
        alias = table.alias if table.alias else table_name
        schema_name = table.db if table.db else None

        ref = TableRef(
            name=table_name,
            alias=alias if alias != table_name else None,
            schema_name=schema_name,
            join_type=None
        )
        tables_by_alias[alias] = ref
        if alias not in [t.alias or t.name for t in tables]:
            tables.append(ref)

    # Find joins
    for join in qualified.find_all(exp.Join):
        join_type = "INNER"
        if join.side:
            join_type = join.side.upper()
        elif join.kind:
            join_type = join.kind.upper()

        table_expr = join.this
        if isinstance(table_expr, exp.Table):
            alias = table_expr.alias if table_expr.alias else table_expr.name
            if alias in tables_by_alias:
                tables_by_alias[alias].join_type = join_type

            if join_type in ("LEFT", "FULL"):
                nullable_aliases.add(alias)
            elif join_type == "RIGHT":
                for prev_alias in list(tables_by_alias.keys())[:-1]:
                    nullable_aliases.add(prev_alias)

    # Build alias to actual table mapping
    alias_to_table = {}
    for t in tables:
        if t.alias:
            alias_to_table[t.alias] = t.name
        alias_to_table[t.name] = t.name

    tables_time = time.time() - tables_start
    if tables_time > 0.1:
        print(f"  extract_tables: {tables_time*1000:.0f}ms", file=sys.stderr)

    # Check if we're selecting from a table-generating function with column definitions
    # (e.g., crosstab(...) AS alias(col1 type1, col2 type2, ...))
    table_alias_columns = {}
    if isinstance(parsed, exp.Select):
        from_clause = parsed.find(exp.From)
        if from_clause and isinstance(from_clause.this, exp.Table):
            table = from_clause.this
            if table.args.get('alias'):
                alias_node = table.args['alias']
                if hasattr(alias_node, 'columns') and alias_node.columns:
                    # Extract column type definitions from TableAlias
                    for col_def in alias_node.columns:
                        col_name = str(col_def.this).strip('"')  # Remove quotes
                        col_type = None
                        if col_def.kind:
                            # Convert sqlglot DataType to PostgreSQL type string
                            dtype = col_def.kind
                            if dtype.this == exp.DataType.Type.INT:
                                col_type = "integer"
                            elif dtype.this == exp.DataType.Type.BIGINT:
                                col_type = "bigint"
                            elif dtype.this == exp.DataType.Type.TEXT:
                                col_type = "text"
                            elif dtype.this == exp.DataType.Type.VARCHAR:
                                col_type = "varchar"
                            elif dtype.this == exp.DataType.Type.DECIMAL:
                                # Include precision and scale if present
                                if dtype.expressions:
                                    params = [str(e.this) for e in dtype.expressions]
                                    col_type = f"numeric({','.join(params)})"
                                else:
                                    col_type = "numeric"
                            elif dtype.this == exp.DataType.Type.NUMERIC:
                                # Include precision and scale if present
                                if dtype.expressions:
                                    params = [str(e.this) for e in dtype.expressions]
                                    col_type = f"numeric({','.join(params)})"
                                else:
                                    col_type = "numeric"
                            else:
                                # For other types, use the string representation
                                col_type = str(dtype.this).lower()
                        table_alias_columns[col_name] = col_type

    # Build reverse index for fast schema lookups (table name -> full key in schema dict)
    # This is much faster than iterating all 395 tables for each column
    table_name_to_schema_key = {}
    for schema_key in sqlglot_schema_dict.keys():
        # schema_key is like "schema.tablename" or just "tablename"
        table_part = schema_key.split('.')[-1]  # Get just the table name
        if table_part not in table_name_to_schema_key:
            table_name_to_schema_key[table_part] = []
        table_name_to_schema_key[table_part].append(schema_key)

    # Extract columns
    columns_start = time.time()
    columns = []
    if isinstance(parsed, exp.Select):
        annotated_select = annotated.find(exp.Select)
        select_expressions = list(annotated_select.expressions) if annotated_select else []

        for i, select_col in enumerate(select_expressions):
            col_name = f"col_{i}"
            alias = None

            if isinstance(select_col, exp.Alias):
                alias = select_col.alias
                col_name = select_col.alias
                inner = select_col.this
            else:
                inner = select_col
                if isinstance(inner, exp.Column):
                    col_name = inner.name

            # Get inferred type
            inferred_type = None
            if hasattr(select_col, 'type') and select_col.type:
                inferred_type = str(select_col.type)

            # Get direct source using the qualified/annotated AST
            source_table, source_col, is_expr = get_direct_column_source(
                annotated, col_name, sqlglot_schema_dict, dialect
            )


            # Check nullable from join
            nullable_from_join = False
            if source_table:
                for a, ref in tables_by_alias.items():
                    if ref.name == source_table and a in nullable_aliases:
                        nullable_from_join = True
                        break

            # Look up schema info
            source_type = None
            source_primary_key = False
            nullable_in_schema = False

            # First check if we have type info from table alias column definitions
            if col_name in table_alias_columns:
                source_type = table_alias_columns[col_name]
                # For table-generating functions, we don't have FK/PK info
                source_primary_key = False
                nullable_in_schema = False
            elif source_table and source_col:
                # Use reverse index for fast lookup (O(1) instead of O(n))
                schema_keys = table_name_to_schema_key.get(source_table, [])
                for schema_key in schema_keys:
                    cols = sqlglot_schema_dict.get(schema_key, {})
                    if source_col in cols:
                        col_schema = cols[source_col]
                        if isinstance(col_schema, dict):
                            source_type = col_schema.get("type")
                            nullable_in_schema = col_schema.get("nullable", False)
                            source_primary_key = col_schema.get("primary_key", False)
                        break

            # Fallback from qualified column
            if not source_table and isinstance(inner, exp.Column) and inner.table:
                actual_table = alias_to_table.get(inner.table, inner.table)
                source_table = actual_table
                source_col = inner.name
                is_expr = False

                if inner.table in nullable_aliases:
                    nullable_from_join = True

            # Get full lineage - DISABLED for performance (origins not used by typo)
            # lineage_start = time.time()
            # origins = trace_column_lineage(clean_sql, col_name, sqlglot_schema_dict, dialect)
            # lineage_time = time.time() - lineage_start
            # if lineage_time > 0.05:
            #     print(f"    trace_lineage for {col_name}: {lineage_time*1000:.0f}ms", file=sys.stderr)
            origins = []  # Not used by typo, but required by JSON schema

            if "vemployeedepartment" in sql.lower():
                print(f"  Column {col_name}: source_table={source_table}, source_col={source_col}", file=sys.stderr)

            columns.append(ColumnLineage(
                name=col_name,
                alias=alias if alias and alias != col_name else None,
                inferred_type=inferred_type,
                nullable_from_join=nullable_from_join,
                nullable_in_schema=nullable_in_schema,
                is_expression=is_expr,
                source_table=source_table,
                source_column=source_col,
                source_type=source_type,
                source_primary_key=source_primary_key,
                origins=origins
            ))

    columns_time = time.time() - columns_start
    if columns_time > 0.1:
        file_info = f" ({path})" if path else ""
        print(f"  extract_columns{file_info}: {columns_time*1000:.0f}ms", file=sys.stderr)

    # Infer parameter types
    param_types_start = time.time()
    if parameters:
        param_type_info = infer_parameter_types(annotated, alias_to_table, dialect)
        for i, param in enumerate(parameters):
            if i < len(param_type_info):
                info = param_type_info[i]
                param.inferred_type = info.get('type')
                param.source_table = info.get('source_table')
                param.source_column = info.get('source_column')
                param.context = info.get('context')

        param_types_time = time.time() - param_types_start
        if param_types_time > 0.1:
            print(f"  infer_parameter_types: {param_types_time*1000:.0f}ms", file=sys.stderr)

    total_time = time.time() - start_time
    if total_time > 0.5:
        file_info = f" ({path})" if path else ""
        print(f"  TOTAL analyze_sql{file_info}: {total_time*1000:.0f}ms", file=sys.stderr)

    # Debug: print source tables for vemployeedepartment
    if "vemployeedepartment" in sql.lower():
        print(f"\nDEBUG vemployeedepartment columns:", file=sys.stderr)
        for col in columns:
            if hasattr(col, 'name') and hasattr(col, 'source_table'):
                print(f"  {col.name}: source_table={col.source_table}", file=sys.stderr)

    return SqlFileResult(
        path="",
        success=True,
        query_type=query_type,
        tables=tables,
        columns=columns,
        parameters=parameters
    )


def dataclass_to_dict(obj):
    """Convert dataclass to dict, handling nested dataclasses and lists."""
    if hasattr(obj, '__dataclass_fields__'):
        return {k: dataclass_to_dict(v) for k, v in asdict(obj).items()}
    elif isinstance(obj, list):
        return [dataclass_to_dict(item) for item in obj]
    elif isinstance(obj, dict):
        return {k: dataclass_to_dict(v) for k, v in obj.items()}
    else:
        return obj


# Global variables for multiprocessing workers (set before pool creation)
_worker_sqlglot_schema = None
_worker_sqlglot_schema_dict = None
_worker_dialect = None


def init_worker(schema, dialect):
    """Initialize worker process with schema (called once per worker)."""
    global _worker_sqlglot_schema, _worker_sqlglot_schema_dict, _worker_dialect
    _worker_sqlglot_schema, _worker_sqlglot_schema_dict = build_sqlglot_schema(schema, dialect)
    _worker_dialect = dialect


def process_single_file(file_info):
    """
    Worker function to process a single SQL file.
    Uses global variables set by init_worker for schema.
    """
    path = file_info.get("path", "")
    content = file_info.get("content", "")

    # Skip empty content
    if not content.strip():
        return {
            "path": path,
            "success": False,
            "error": "Empty SQL content"
        }

    # Remove SQL comments for cleaner parsing
    lines = content.split('\n')
    sql_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith('--'):
            sql_lines.append(line)
    sql = '\n'.join(sql_lines).strip()

    if not sql:
        sql = content

    try:
        result = analyze_sql(sql, _worker_sqlglot_schema, _worker_sqlglot_schema_dict, _worker_dialect, path)
        result.path = path
        return dataclass_to_dict(result)
    except Exception as e:
        return {
            "path": path,
            "success": False,
            "error": str(e)
        }


def main():
    # Read JSON input from stdin
    input_data = json.load(sys.stdin)

    dialect = input_data.get("dialect", "postgres")
    schema = input_data.get("schema", {})
    files = input_data.get("files", [])

    # Check for parallelization env var (default: enabled)
    # Set TYPO_SQLGLOT_PARALLEL=0 to disable parallel processing
    enable_parallel = os.environ.get("TYPO_SQLGLOT_PARALLEL", "1") != "0"

    # Determine number of workers (default: use all available CPUs)
    # Can be overridden with TYPO_SQLGLOT_WORKERS env var
    num_workers = int(os.environ.get("TYPO_SQLGLOT_WORKERS", "0"))
    if num_workers <= 0:
        num_workers = multiprocessing.cpu_count()

    start_time = time.time()

    # For small batches or when parallelization is disabled, use sequential processing
    if not enable_parallel or len(files) <= 1:
        # Build sqlglot schema ONCE upfront (expensive operation)
        schema_start = time.time()
        sqlglot_schema, sqlglot_schema_dict = build_sqlglot_schema(schema, dialect)
        schema_time = time.time() - schema_start
        if schema_time > 0.1:
            print(f"Schema build time: {schema_time*1000:.0f}ms for {len(schema)} tables", file=sys.stderr)

        results = []

        for file_info in files:
            path = file_info.get("path", "")
            content = file_info.get("content", "")

            # Skip empty content
            if not content.strip():
                results.append({
                    "path": path,
                    "success": False,
                    "error": "Empty SQL content"
                })
                continue

            # Remove SQL comments for cleaner parsing
            lines = content.split('\n')
            sql_lines = []
            for line in lines:
                stripped = line.strip()
                if stripped and not stripped.startswith('--'):
                    sql_lines.append(line)
            sql = '\n'.join(sql_lines).strip()

            if not sql:
                sql = content

            try:
                result = analyze_sql(sql, sqlglot_schema, sqlglot_schema_dict, dialect, path)
                result.path = path
                results.append(dataclass_to_dict(result))
            except Exception as e:
                results.append({
                    "path": path,
                    "success": False,
                    "error": str(e)
                })
    else:
        # Parallel processing with multiprocessing.Pool
        print(f"Processing {len(files)} files using {num_workers} workers...", file=sys.stderr)

        # Use multiprocessing pool with initializer to build schema once per worker
        # This is much more efficient than rebuilding for each file
        with multiprocessing.Pool(
            processes=num_workers,
            initializer=init_worker,
            initargs=(schema, dialect)
        ) as pool:
            results = pool.map(process_single_file, files)

    total_time = time.time() - start_time
    print(f"Total processing time: {total_time*1000:.0f}ms for {len(files)} files", file=sys.stderr)

    # Output JSON result
    output = {"results": results}
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
