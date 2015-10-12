package foo

fun test() {
  A.d
  A.Companion.<!NONE_APPLICABLE!>f<!> // todo happened because we have two f here -- as Member of A.Companion and as static object A.Companion
  A.Companion.g
  B.<!INVISIBLE_MEMBER!>D<!>
  <!INVISIBLE_MEMBER!>CCC<!>
  CCC.<!INVISIBLE_MEMBER!>classObjectVar<!>
}

class A() {
  public companion object {
    val d = 3
    private object f {

    }
    object g
  }
}

class B {
    class D {
        private companion object
    }
}

class CCC() {
  private companion object {
    val classObjectVar = 3
  }
}