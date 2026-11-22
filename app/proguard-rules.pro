# Room entity & DAO được sinh mã, giữ lại metadata cần thiết
-keep class vn.vigo.nepnha.data.db.** { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker { public <init>(...); }
