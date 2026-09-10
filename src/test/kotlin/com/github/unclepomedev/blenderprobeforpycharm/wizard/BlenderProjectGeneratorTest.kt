package com.github.unclepomedev.blenderprobeforpycharm.wizard

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManagerListener
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BlenderProjectGeneratorTest : BaseBlenderTest() {

    private fun waitForTaskCompletion(taskTitle: String, action: () -> Unit) {
        val taskLatch = CountDownLatch(1)
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(
            ProgressManagerListener.TOPIC,
            object : ProgressManagerListener {
                override fun afterTaskFinished(task: Task) {
                    if (task.title == taskTitle) {
                        taskLatch.countDown()
                    }
                }
            },
        )

        action()

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (taskLatch.count > 0 && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
        assertTrue(
            "Timed out waiting for task '$taskTitle' to finish",
            taskLatch.await(0, TimeUnit.MILLISECONDS),
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    fun testGenerateProjectStructure() {
        val generator = BlenderProjectGenerator()
        val settings = BlenderSettings.getInstance(project)

        settings.state.blenderPath = "/dummy/path/to/blender"

        val basePath = project.basePath ?: error("Project base path is null")
        val baseDir =
            LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: error("VirtualFile not found for $basePath")
        val module = myFixture.module

        waitForTaskCompletion("Configuring Blender environment") {
            generator.generateProject(project, baseDir, Any(), module)
        }
        baseDir.refresh(false, true)

        assertNotNull("License should be created", baseDir.findChild("LICENSE"))

        val gitignore = baseDir.findChild(".gitignore")
        assertNotNull("GitIgnore should be created", gitignore)
        gitignore?.let {
            val content = VfsUtil.loadText(it)
            assertTrue("Should ignore .blender_stubs", content.contains(".blender_stubs/"))
            assertTrue("Should ignore .vscode", content.contains(".vscode/"))
            assertTrue("Should ignore .venv", content.contains(".venv/"))
        }

        val pyproject = baseDir.findChild("pyproject.toml")
        assertNotNull("pyproject.toml should be created", pyproject)
        pyproject?.let {
            val content = VfsUtil.loadText(it)
            val expectedSlug = BlenderProbeUtils.normalizeModuleName(project.name)
            assertTrue(
                "Should contain project name",
                content.contains("name = \"$expectedSlug\""),
            )
            assertTrue(
                "Should contain pip dependency",
                content.contains("dependencies = [\"pip\"]"),
            )
            assertTrue(
                "Should use hatchling",
                content.contains("build-backend = \"hatchling.build\""),
            )
        }

        val expectedSlug = BlenderProbeUtils.normalizeModuleName(project.name)
        val packageDir = baseDir.findChild(expectedSlug)

        assertNotNull("Package directory '$expectedSlug' should exist", packageDir)
        packageDir?.let { dir ->
            assertNotNull("__init__.py missing", dir.findChild("__init__.py"))
            assertNotNull("operators.py missing", dir.findChild("operators.py"))
            assertNotNull("panel.py missing", dir.findChild("panel.py"))

            val manifestFile = dir.findChild("blender_manifest.toml")
            assertNotNull("Manifest should be inside package dir", manifestFile)

            if (manifestFile != null) {
                val manifestContent = VfsUtil.loadText(manifestFile)
                assertFalse("Placeholder replaced", manifestContent.contains($$"${ADDON_NAME}"))
                assertTrue("Slug injected", manifestContent.contains("id = \"$expectedSlug\""))
                assertTrue(
                    "GPL License should be specified",
                    manifestContent.contains("SPDX:GPL-3.0-or-later"),
                )
                assertTrue(
                    "Wheels array should be declared",
                    manifestContent.contains("wheels = ["),
                )
                assertTrue(
                    "Build section should be present",
                    manifestContent.contains("[build]"),
                )
            }

            val wheelsDir = dir.findChild("wheels")
            assertNotNull("wheels directory should exist next to the manifest", wheelsDir)
            assertNotNull(
                "wheels/README.md should document how to add wheels",
                wheelsDir?.findChild("README.md"),
            )
        }

        val testsDir = baseDir.findChild("tests")
        assertNotNull("tests directory should exist", testsDir)
        testsDir?.let { dir ->
            assertNotNull("test_sample.py missing", dir.findChild("test_sample.py"))
            assertNotNull(
                "run_tests.py missing (Required for CI)",
                dir.findChild("run_tests.py"),
            )
        }

        val githubDir = baseDir.findChild(".github")
        assertNotNull(".github directory should exist", githubDir)

        val workflowsDir = githubDir?.findChild("workflows")
        assertNotNull("workflows directory should exist", workflowsDir)

        workflowsDir?.let { dir ->
            val ciFile = dir.findChild("ci.yml")
            assertNotNull("CI workflow file missing", ciFile)
            ciFile?.let {
                val content = VfsUtil.loadText(it)
                assertTrue(
                    "Should run on ubuntu-latest",
                    content.contains("runs-on: ubuntu-latest"),
                )
                assertTrue("Should contain matrix strategy", content.contains("matrix:"))
            }
        }

        val dependabot = githubDir?.findChild("dependabot.yml")
        assertNotNull("dependabot.yml missing", dependabot)
    }

    fun testGenerateProjectNoBlenderExecutableDoesNotNotify() {
        val generator = BlenderProjectGenerator()
        val settings = BlenderSettings.getInstance(project)

        val notifications = mutableListOf<Notification>()
        project.messageBus
            .connect(testRootDisposable)
            .subscribe(
                Notifications.TOPIC,
                object : Notifications {
                    override fun notify(notification: Notification) {
                        notifications.add(notification)
                    }
                },
            )

        settings.state.blenderPath = ""
        settings.state.entries.clear()
        settings.state.currentEntryName = ""

        val basePath = project.basePath ?: error("Project base path is null")
        val baseDir =
            LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: error("VirtualFile not found for $basePath")
        val module = myFixture.module

        waitForTaskCompletion("Configuring Blender environment") {
            generator.generateProject(project, baseDir, Any(), module)
        }

        assertTrue(
            "No notification should be shown when Blender executable is missing",
            notifications.isEmpty(),
        )
    }

    fun testScheduleStubGenerationFailureNotifiesUser() {
        val generator = BlenderProjectGenerator()
        val settings = BlenderSettings.getInstance(project)

        val notifications = mutableListOf<Notification>()
        val notificationLatch = CountDownLatch(1)
        project.messageBus
            .connect(testRootDisposable)
            .subscribe(
                Notifications.TOPIC,
                object : Notifications {
                    override fun notify(notification: Notification) {
                        notifications.add(notification)
                        notificationLatch.countDown()
                    }
                },
            )

        settings.state.blenderPath = "/nonexistent/dummy/blender"

        val basePath = project.basePath ?: error("Project base path is null")
        val baseDir =
            LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: error("VirtualFile not found for $basePath")
        val module = myFixture.module

        waitForTaskCompletion("Configuring Blender environment") {
            generator.generateProject(project, baseDir, Any(), module)
        }

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (notificationLatch.count > 0 && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
        assertTrue(
            "Timed out waiting for notification",
            notificationLatch.await(0, TimeUnit.MILLISECONDS),
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("Exactly one notification should be published", 1, notifications.size)
        val notification = notifications[0]
        assertEquals("Blender Probe Notification Group", notification.groupId)
        assertEquals("Generation Failed", notification.title)
        assertTrue(
            "Notification content should contain failure message",
            notification.content.contains("Failed to generate stubs:"),
        )
    }
}
