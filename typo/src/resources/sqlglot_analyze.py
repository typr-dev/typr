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
from dataclasses import dataclass, field, asdict
from typing import Optional, Any
import re

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
    Returns modified SQL and list of parameters.
    """
    params = []
    position = 0

    # Pattern for typo parameters: :name, :"name!", :"name?", :name:Type!
    pattern = r':("?)([a-zA-Z_][a-zA-Z0-9_]*)((?::[a-zA-Z_][a-zA-Z0-9_.]*)?[!?]?)(\1)'

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

    modified_sql = re.sub(pattern, replace_param, sql)
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


def infer_parameter_types(annotated_ast, alias_to_table: dict) -> list[dict]:
    """
    Walk the annotated AST to find ALL placeholders and infer their types from context.
    """
    results = []

    placeholders = []
    collect_placeholders_in_order(annotated_ast, placeholders)

    for node in placeholders:
        parent = node.parent
        info = {
            'type': None,
            'source_table': None,
            'source_column': None,
            'context': None
        }

        if isinstance(parent, exp.Is):
            info['context'] = 'IS NULL'

        elif isinstance(parent, (exp.EQ, exp.NEQ, exp.GT, exp.GTE, exp.LT, exp.LTE)):
            if parent.left == node:
                other = parent.right
            else:
                other = parent.left

            other_type = other.type if hasattr(other, 'type') else None
            info['type'] = str(other_type) if other_type else None
            info['context'] = type(parent).__name__

            if isinstance(other, exp.Column):
                alias = other.table
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
                info['type'] = cast_type.sql(dialect='mysql')
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

    except Exception:
        pass

    return origins


def get_direct_column_source(sql: str, column_name: str, schema_dict: dict, dialect: str) -> tuple[Optional[str], Optional[str], bool]:
    """
    Get the direct source table and column for a result column.
    Returns (source_table, source_column, is_expression)
    """
    try:
        node = lineage(column_name, sql, schema=schema_dict, dialect=dialect)

        if node.downstream:
            for downstream in node.downstream:
                source = downstream.source

                if isinstance(source, exp.Table):
                    table_name = source.name
                    parts = downstream.name.split('.')
                    col_name = parts[-1] if parts else downstream.name
                    return table_name, col_name, False

                if isinstance(source, exp.Select):
                    if isinstance(downstream.expression, exp.Alias):
                        inner = downstream.expression.this
                        if isinstance(inner, exp.Column):
                            for inner_col in source.find_all(exp.Column):
                                if inner_col.name == inner.name and inner_col.table:
                                    inner_table = inner_col.table
                                    if inner_table in schema_dict:
                                        return inner_table, inner_col.name, False
                        else:
                            return None, None, True

        return None, None, False
    except Exception:
        return None, None, False


def analyze_sql(sql: str, schema: dict, dialect: str) -> SqlFileResult:
    """Analyze a single SQL query."""

    # Extract parameters first
    clean_sql, parameters = extract_parameters(sql)

    # Parse the SQL
    try:
        parsed = sqlglot.parse_one(clean_sql, dialect=dialect)
    except Exception as e:
        return SqlFileResult(
            path="",
            success=False,
            error=f"Parse error: {str(e)}",
            parameters=[asdict(p) for p in parameters]
        )

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

    # Build sqlglot schema
    sqlglot_schema = MappingSchema(dialect=dialect)
    sqlglot_schema_dict = {}

    for table_name, columns in schema.items():
        table_cols = {}
        for col_name, col_info in columns.items():
            if isinstance(col_info, dict):
                col_type = col_info.get("type", "VARCHAR")
                try:
                    dtype = exp.DataType.build(col_type, dialect=dialect)
                    table_cols[col_name] = dtype
                except Exception:
                    # Handle types sqlglot can't parse
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
                        table_cols[col_name] = col_type
            else:
                table_cols[col_name] = col_info

        try:
            sqlglot_schema.add_table(table_name, table_cols)
        except Exception:
            pass  # Skip tables that can't be added

        sqlglot_schema_dict[table_name] = {
            k: str(v) if isinstance(v, exp.DataType) else v
            for k, v in table_cols.items()
        }

    # Try to qualify and annotate
    try:
        qualified = qualify(parsed, schema=sqlglot_schema, dialect=dialect)
    except Exception:
        qualified = parsed

    try:
        annotated = annotate_types(qualified, schema=sqlglot_schema, dialect=dialect)
    except Exception:
        annotated = qualified

    # Extract table references
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

    # Extract columns
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

            # Get direct source
            source_table, source_col, is_expr = get_direct_column_source(
                clean_sql, col_name, sqlglot_schema_dict, dialect
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

            if source_table and source_col:
                # Try to find in schema (with various key formats)
                for schema_table, cols in schema.items():
                    if schema_table.endswith(f".{source_table}") or schema_table == source_table:
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

            # Get full lineage
            origins = trace_column_lineage(clean_sql, col_name, sqlglot_schema_dict, dialect)

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

    # Infer parameter types
    if parameters:
        param_type_info = infer_parameter_types(annotated, alias_to_table)
        for i, param in enumerate(parameters):
            if i < len(param_type_info):
                info = param_type_info[i]
                param.inferred_type = info.get('type')
                param.source_table = info.get('source_table')
                param.source_column = info.get('source_column')
                param.context = info.get('context')

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


def main():
    # Read JSON input from stdin
    input_data = json.load(sys.stdin)

    dialect = input_data.get("dialect", "postgres")
    schema = input_data.get("schema", {})
    files = input_data.get("files", [])

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
            result = analyze_sql(sql, schema, dialect)
            result.path = path
            results.append(dataclass_to_dict(result))
        except Exception as e:
            results.append({
                "path": path,
                "success": False,
                "error": str(e)
            })

    # Output JSON result
    output = {"results": results}
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
