import java.util.*

public fun arrayListOf<T>(vararg values: T): ArrayList<T> = values.toCollection(ArrayList())
