package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

/** Utility to register generated stubs folder as a source root for the project. */
internal object BlenderStubSourceRootManager {

    /**
     * Marks the specified directory as a source root of the module enclosing the project base path.
     *
     * @param project The active project.
     * @param dir The virtual file representing the directory to add as a source folder.
     */
    fun markAsSourceRoot(project: Project, dir: VirtualFile) {
        val basePath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        ReadAction.nonBlocking(
                Callable {
                    if (project.isDisposed) return@Callable null
                    ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(baseDir)
                }
            )
            .expireWhen { project.isDisposed }
            .finishOnUiThread(ModalityState.defaultModalityState()) { module ->
                if (module != null && !module.isDisposed) {
                    ApplicationManager.getApplication().runWriteAction {
                        ModuleRootModificationUtil.updateModel(module) { model ->
                            val contentEntry =
                                model.contentEntries.find { entry ->
                                    entry.file?.let { VfsUtil.isAncestor(it, dir, false) } == true
                                } ?: return@updateModel

                            if (contentEntry.sourceFolders.none { it.url == dir.url }) {
                                contentEntry.addSourceFolder(dir, false)
                            }
                        }
                    }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
