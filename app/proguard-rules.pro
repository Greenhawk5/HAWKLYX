# Keep WorkManager internal implementation classes created via reflection
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
-keep class androidx.work.impl.** {
    public <init>(...);
}
