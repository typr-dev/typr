package typr.dsl.internal

import doobie.util.fragment.Fragment

object mkFragment {
  implicit class FragmentOps[I[t] <: Iterable[t]](fragments: I[Fragment]) {
    def mkFragment(sep: Fragment): Fragment = {
      fragments.reduceOption((a, b) => a ++ sep ++ b).getOrElse(Fragment.empty)
    }
  }

}
