fun box () : String {
    val s = java.util.ArrayList<String>()
    s.add("foo")
    s[0] += "bar"
    return if(s[0] == "foobar") "OK" else "fail"
}


