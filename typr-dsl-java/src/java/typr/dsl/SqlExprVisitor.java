package typr.dsl;

/**
 * Visitor pattern for SqlExpr to enable type-safe operations without casting. This handles the
 * existential types that arise from pattern matching on GADT-like structures.
 */
public interface SqlExprVisitor<T, Row, Result> {

  Result visitFieldLike(SqlExpr.FieldLike<T, Row> field);

  Result visitConstReq(SqlExpr.ConstReq<T> constReq);

  Result visitConstOpt(SqlExpr.ConstOpt<T> constOpt);

  Result visitArrayIndex(SqlExpr.ArrayIndex<T> arrayIndex);

  <A> Result visitApply1(SqlExpr.Apply1<A, T> apply1);

  <A, B> Result visitApply2(SqlExpr.Apply2<A, B, T> apply2);

  <A, B, C> Result visitApply3(SqlExpr.Apply3<A, B, C, T> apply3);

  <A, B> Result visitBinary(SqlExpr.Binary<A, B, T> binary);

  <U> Result visitUnderlying(SqlExpr.Underlying<U, T> underlying);

  Result visitCoalesce(SqlExpr.Coalesce<T> coalesce);

  // Boolean-returning expressions - these always return Boolean regardless of T
  Result visitInExpr(SqlExpr.In<?> in);

  Result visitIsNullExpr(SqlExpr.IsNull<?> isNull);

  <Tuple> Result visitCompositeInExpr(SqlExpr.CompositeIn<Tuple, ?> compositeIn);

  <F extends Tuples.TupleExpr<R>, R extends Tuples.Tuple> Result visitInSubqueryExpr(
      SqlExpr.InSubquery<?, F, R> inSubquery);

  <F, R> Result visitExistsExpr(SqlExpr.Exists<F, R> exists);

  // These can return T
  Result visitNot(SqlExpr.Not<T> not);

  Result visitRowExpr(SqlExpr.RowExpr rowExpr);

  Result visitTupleExpr(Tuples.TupleExpr<?> tupleExpr);

  Result visitFieldsExpr(FieldsExpr<?> fieldsExpr);

  <U> Result visitIncludeIf(SqlExpr.IncludeIf<U> includeIf);

  // Aggregate functions
  Result visitCountStar(SqlExpr.CountStar countStar);

  <U> Result visitCount(SqlExpr.Count<U> count);

  <U> Result visitCountDistinct(SqlExpr.CountDistinct<U> countDistinct);

  <U, R> Result visitSum(SqlExpr.Sum<U, R> sum);

  <U> Result visitAvg(SqlExpr.Avg<U> avg);

  <U> Result visitMin(SqlExpr.Min<U> min);

  <U> Result visitMax(SqlExpr.Max<U> max);

  Result visitStringAgg(SqlExpr.StringAgg stringAgg);

  <U> Result visitArrayAgg(SqlExpr.ArrayAgg<U> arrayAgg);

  <U> Result visitJsonAgg(SqlExpr.JsonAgg<U> jsonAgg);

  Result visitBoolAnd(SqlExpr.BoolAnd boolAnd);

  Result visitBoolOr(SqlExpr.BoolOr boolOr);

  Result visitDefault(SqlExpr<T> expr);

  /**
   * Accept method that performs all necessary casts and dispatches to the appropriate visit method.
   */
  @SuppressWarnings("unchecked")
  default Result accept(SqlExpr<T> expr) {
    return switch (expr) {
      case SqlExpr.FieldLike<?, ?> fieldLike -> {
        SqlExpr.FieldLike<T, Row> typed = (SqlExpr.FieldLike<T, Row>) fieldLike;
        yield visitFieldLike(typed);
      }
      case SqlExpr.ConstReq<?> constReq -> {
        SqlExpr.ConstReq<T> typed = (SqlExpr.ConstReq<T>) constReq;
        yield visitConstReq(typed);
      }
      case SqlExpr.ConstOpt<?> constOpt -> {
        SqlExpr.ConstOpt<T> typed = (SqlExpr.ConstOpt<T>) constOpt;
        yield visitConstOpt(typed);
      }
      case SqlExpr.ArrayIndex<?> arrayIndex -> {
        SqlExpr.ArrayIndex<T> typed = (SqlExpr.ArrayIndex<T>) arrayIndex;
        yield visitArrayIndex(typed);
      }
      case SqlExpr.Apply1<?, ?> apply1 -> {
        SqlExpr.Apply1<?, T> typed = (SqlExpr.Apply1<?, T>) apply1;
        yield visitApply1(typed);
      }
      case SqlExpr.Apply2<?, ?, ?> apply2 -> {
        SqlExpr.Apply2<?, ?, T> typed = (SqlExpr.Apply2<?, ?, T>) apply2;
        yield visitApply2(typed);
      }
      case SqlExpr.Apply3<?, ?, ?, ?> apply3 -> {
        SqlExpr.Apply3<?, ?, ?, T> typed = (SqlExpr.Apply3<?, ?, ?, T>) apply3;
        yield visitApply3(typed);
      }
      case SqlExpr.Binary<?, ?, ?> binary -> {
        SqlExpr.Binary<?, ?, T> typed = (SqlExpr.Binary<?, ?, T>) binary;
        yield visitBinary(typed);
      }
      case SqlExpr.Underlying<?, ?> underlying -> {
        SqlExpr.Underlying<?, T> typed = (SqlExpr.Underlying<?, T>) underlying;
        yield visitUnderlying(typed);
      }
      case SqlExpr.Coalesce<?> coalesce -> {
        SqlExpr.Coalesce<T> typed = (SqlExpr.Coalesce<T>) coalesce;
        yield visitCoalesce(typed);
      }
      case SqlExpr.In<?> in -> visitInExpr(in);
      case SqlExpr.IsNull<?> isNull -> visitIsNullExpr(isNull);
      case SqlExpr.Not<?> not -> {
        SqlExpr.Not<T> typed = (SqlExpr.Not<T>) not;
        yield visitNot(typed);
      }
      case SqlExpr.RowExpr rowExpr -> visitRowExpr(rowExpr);
      case SqlExpr.CompositeIn<?, ?> compositeIn -> visitCompositeInExpr(compositeIn);
      case SqlExpr.InSubquery<?, ?, ?> inSubquery -> visitInSubqueryExpr(inSubquery);
      case SqlExpr.Exists<?, ?> exists -> visitExistsExpr(exists);
      case SqlExpr.IncludeIf<?> includeIf -> visitIncludeIf(includeIf);
      // Aggregate functions
      case SqlExpr.CountStar countStar -> visitCountStar(countStar);
      case SqlExpr.Count<?> count -> visitCount(count);
      case SqlExpr.CountDistinct<?> countDistinct -> visitCountDistinct(countDistinct);
      case SqlExpr.Sum<?, ?> sum -> visitSum(sum);
      case SqlExpr.Avg<?> avg -> visitAvg(avg);
      case SqlExpr.Min<?> min -> visitMin(min);
      case SqlExpr.Max<?> max -> visitMax(max);
      case SqlExpr.StringAgg stringAgg -> visitStringAgg(stringAgg);
      case SqlExpr.ArrayAgg<?> arrayAgg -> visitArrayAgg(arrayAgg);
      case SqlExpr.JsonAgg<?> jsonAgg -> visitJsonAgg(jsonAgg);
      case SqlExpr.BoolAnd boolAnd -> visitBoolAnd(boolAnd);
      case SqlExpr.BoolOr boolOr -> visitBoolOr(boolOr);
      case Tuples.TupleExpr<?> tupleExpr -> visitTupleExpr(tupleExpr);
      case FieldsExpr<?> fieldsExpr -> visitFieldsExpr(fieldsExpr);
    };
  }
}
