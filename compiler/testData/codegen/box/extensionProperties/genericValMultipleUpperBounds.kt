import java.io.Serializable

val <T> T.valProp: T where T : Number, T : Serializable
    get() = this

fun box(): String {
    0.valProp

    return "OK"
}


