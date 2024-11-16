package d.zhdanov.ccfit.nsu.core.utils

@Target(
  AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.SOURCE)
annotation class SynchronizedUsage
