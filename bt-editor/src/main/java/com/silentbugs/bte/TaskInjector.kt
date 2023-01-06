package com.silentbugs.bte

import com.badlogic.gdx.ai.btree.Task

/**
 * TaskInjector that should inject dependencies into [Task]s and its children if any
 *
 * @see {@link AIEditor.setTaskInjector
 */
interface TaskInjector {
    /**
     * Inject dependencies into given [Task] and its children
     *
     * @param task task to inject
     */
    fun inject(task: Task<*>)
}
