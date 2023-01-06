package com.silentbugs.bte

/**
 * Mark Task class or Task field with a userComment
 * Attribute edit fields will show this info
 * If Task already has a userComment in the tree file both will be shown
 * Created by PiotrJ on 16/10/15.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class TaskComment(
    val value: String = "",
    /**
     * @return if field name should be hidden, only comment will be shown
     */
    val skipFieldName: Boolean = true
)
