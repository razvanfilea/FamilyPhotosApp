-allowaccessmodification

# Strip Kotlin's runtime null checks on parameters/platform types.
# (Not checkNotNull(Object): that is `!!`, removing it would change behavior.)
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void checkParameterIsNotNull(...);
}
