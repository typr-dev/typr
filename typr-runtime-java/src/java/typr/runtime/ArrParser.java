package typr.runtime;

import java.util.ArrayList;
import java.util.function.Function;
import typr.data.Arr;

public interface ArrParser {
  enum ParseState {
    ExpectArray,
    ExpectDatum,
    InDatumQuoted,
    InDatumUnquoted,
    InEscape,
    ElemComplete,
    Done
  }

  static Either<String, Arr<String>> parse(String s) {
    return parseWith(Either::right, s);
  }

  static <A> Either<String, Arr<A>> parseWith(Function<String, Either<String, A>> f, String s) {
    if (s.equals("{}")) {
      return Either.right(Arr.empty());
    } else {
      int sLength = s.length();
      int dataDepth = sLength - s.replaceAll("^\\{+", "").length();
      ArrayList<A> data = new ArrayList<>();
      StringBuilder datum = new StringBuilder();
      int index = 0;
      int depth = 0;
      int[] curCount = new int[dataDepth + 1];
      int[] refCount = new int[dataDepth + 1];
      String failure = null;
      ParseState state = ParseState.ExpectArray;
      while (index < sLength && failure == null) {
        char c = s.charAt(index);
        var cs = Character.toString(c);
        switch (state) {
          case Done:
            failure = "expected end of string, found " + c;
            break;
          case ExpectArray:
            if (c == '{') {
              index++;
              depth++;
              state = depth == dataDepth ? ParseState.ExpectDatum : ParseState.ExpectArray;
            } else {
              failure = "expected '{', found " + c;
            }
            break;
          case ExpectDatum:
            if (c == '{' || c == '}' || c == ',' || c == '\\') {
              failure = "expected datum, found '" + c + "'";
            } else if (c == '"') {
              index++;
              datum.setLength(0);
              state = ParseState.InDatumQuoted;
            } else {
              index++;
              datum.setLength(0);
              datum.append(c);
              state = ParseState.InDatumUnquoted;
            }
            break;
          case InDatumQuoted:
            if (c == '"') {
              Either<String, A> result = f.apply(datum.toString());
              switch (result) {
                case Either.Left<String, A> l -> failure = l.value();
                case Either.Right<String, A> r -> data.add(r.value());
              }
              index++;
              state = ParseState.ElemComplete;
            } else if (c == '\\') {
              index++;
              state = ParseState.InEscape;
            } else {
              datum.append(c);
              index++;
            }
            break;
          case InDatumUnquoted:
            if (c == '{' || c == '\\') {
              failure = "illegal character in unquoted datum: '" + c + "'";
            } else if (c == ',') {
              if (datum.toString().equalsIgnoreCase("null")) {
                failure = "encountered NULL array element (currently unsupported)";
              }
              updateCountersAfterComma(curCount, refCount, depth);
              Either<String, A> result = f.apply(datum.toString());
              switch (result) {
                case Either.Left<String, A> l -> failure = l.value();
                case Either.Right<String, A> r -> data.add(r.value());
              }
              index++;
              state = ParseState.ExpectDatum;
            } else if (c == '}') {
              if (datum.toString().equalsIgnoreCase("null")) {
                failure = "encountered NULL array element (currently unsupported)";
              }
              updateCountersAfterClose(curCount, refCount, depth);
              Either<String, A> result = f.apply(datum.toString());
              switch (result) {
                case Either.Left<String, A> l -> failure = l.value();
                case Either.Right<String, A> r -> data.add(r.value());
              }
              index++;
              depth--;
              state = depth == 0 ? ParseState.Done : ParseState.ElemComplete;
            } else {
              datum.append(c);
              index++;
            }
            break;
          case InEscape:
            datum.append(c);
            index++;
            state = ParseState.InDatumQuoted;
            break;
          case ElemComplete:
            if (c == ',') {
              updateCountersAfterComma(curCount, refCount, depth);
              index++;
              state = depth == dataDepth ? ParseState.ExpectDatum : ParseState.ExpectArray;
            } else if (c == '}') {
              updateCountersAfterClose(curCount, refCount, depth);
              index++;
              depth--;
              if (depth == 0) {
                state = ParseState.Done;
              }
            } else {
              failure = "expected ',' or '}', found " + c;
            }
            break;
        }
      }

      if (failure != null) {
        return Either.left(failure);
      } else if (depth != 0 || state != ParseState.Done) {
        return Either.left("unterminated array literal");
      } else {
        return Either.right(new Arr<>(data.toArray(), refCount));
      }
    }
  }

  private static void updateCountersAfterComma(int[] curCount, int[] refCount, int depth) {
    int ref = refCount[depth];
    int cur = curCount[depth];
    int inc = cur + 1;
    if (ref > 0 && inc == ref) {
      throw new RuntimeException("expected " + ref + " element(s) here; found more");
    } else {
      curCount[depth] = inc;
    }
  }

  private static void updateCountersAfterClose(int[] curCount, int[] refCount, int depth) {
    int ref = refCount[depth];
    int cur = curCount[depth];
    int inc = cur + 1;
    if (ref > 0 && inc < ref) {
      throw new RuntimeException("expected " + ref + " element here, only found " + inc);
    } else {
      curCount[depth] = 0;
      refCount[depth] = inc;
    }
  }
}
