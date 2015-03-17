val receiver = { <!DEPRECATED_LAMBDA_SYNTAX!>Int.()<!> -> }
val receiverAndReturnType = { <!DEPRECATED_LAMBDA_SYNTAX!>Int.(): Int<!> ->  5 }
val returnType = { <!DEPRECATED_LAMBDA_SYNTAX!>(): Int<!> -> 5 }

val receiverWithFunctionType = { <!DEPRECATED_LAMBDA_SYNTAX!>((Int) -> Int).()<!> -> }

val none = { -> }


val parameterWithFunctionType = { <!UNRESOLVED_REFERENCE!>a<!>: ((Int) -> Int) -> <!SYNTAX!><!>} // todo fix parser

val newSyntax = { a: Int -> }
val newSyntax1 = { <!CANNOT_INFER_PARAMETER_TYPE!>a<!>, <!CANNOT_INFER_PARAMETER_TYPE!>b<!> -> }
val newSyntax2 = { a: Int, b: Int -> }
val newSyntax3 = { <!CANNOT_INFER_PARAMETER_TYPE!>a<!>, b: Int -> }
val newSyntax4 = { a: Int, <!CANNOT_INFER_PARAMETER_TYPE!>b<!> -> }