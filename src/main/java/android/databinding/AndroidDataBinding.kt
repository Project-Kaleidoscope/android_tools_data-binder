/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2022 Project Kaleidoscope
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("MemberVisibilityCanPrivate")

package android.databinding

import android.databinding.cli.GenerateBaseClassesOptions
import android.databinding.cli.ProcessXmlOptions
import android.databinding.utils.ZipUtil
import android.databinding.tool.BaseDataBinder
import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.LayoutXmlProcessor
import android.databinding.tool.store.LayoutInfoInput
import android.databinding.tool.util.L
import android.databinding.tool.writer.JavaFileWriter
import com.beust.jcommander.JCommander
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

object AndroidDataBinding {
    private const val JAR_FILE = "data-binder-fat.jar"
    private const val PROCESS_RESOURCES = "PROCESS_RESOURCES"
    private const val GEN_BASE_CLASSES = "GEN_BASE_CLASSES"

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsageAndExit()
        }
        try {
            when(args[0]) {
                PROCESS_RESOURCES -> {
                    val processXmlOptions = ProcessXmlOptions()
                    val jCommander = JCommander(processXmlOptions)
                    jCommander.parse(*(args.drop(1).toTypedArray()))
                    processResources(processXmlOptions)
                }
                GEN_BASE_CLASSES -> {
                    val genBaseClassOptions = GenerateBaseClassesOptions()
                    val jCommander = JCommander(genBaseClassOptions)
                    jCommander.parse(*(args.drop(1).toTypedArray()))
                    generateBaseClasses(genBaseClassOptions)
                }
                else -> {
                    printUsageAndExit()
                }
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    private fun printUsageAndExit() {
        val commander = JCommander()
        commander.programName = JAR_FILE

        commander.addCommand(PROCESS_RESOURCES, ProcessXmlOptions())
        commander.addCommand(GEN_BASE_CLASSES, GenerateBaseClassesOptions())

        println("This binary can be used to either process xml resources or generate " +
                "base classes for data binding.")
        println()
        commander.usage()

        exitProcess(1)
    }

    @JvmStatic
    fun processResources(processXmlOptions: ProcessXmlOptions) {
        val processor = createXmlProcessor(processXmlOptions)
        val resOutputDir = if (processXmlOptions.shouldZipResOutput()) {
            Files.createTempDirectory("db-resources-out").toFile()
        } else {
            processXmlOptions.resOutput.absoluteFile
        }
        val input = LayoutXmlProcessor.ResourceInput(
                false,
                processXmlOptions.resInput.absoluteFile,
                resOutputDir
        )
        L.setDebugLog(true)
        processor.processResources(input,
            processXmlOptions.isEnableViewBinding, processXmlOptions.isEnableDataBinding)
        if (processXmlOptions.shouldZipLayoutInfo()) {
            val outDir : File
            val zipFile : File
            if (processXmlOptions.layoutInfoOutput.toString().lowercase().endsWith(".zip")) {
                outDir = processXmlOptions.layoutInfoOutput.parentFile
                zipFile = processXmlOptions.layoutInfoOutput
            } else {
                outDir = processXmlOptions.layoutInfoOutput
                zipFile = File(outDir, "layout-info.zip")
            }
            FileUtils.forceMkdir(outDir)
            val zfw = ZipFileWriter(zipFile)
            processor.writeLayoutInfoFiles(outDir, zfw)
            zfw.close()
            L.d("writing info zip to ${zipFile.canonicalPath}")
        } else {
            processor.writeLayoutInfoFiles(processXmlOptions.layoutInfoOutput)
        }
        if (processXmlOptions.shouldZipResOutput()) {
            ZipUtil.zip(resOutputDir, processXmlOptions.resOutput)
        }
    }

    /**
     * Creates data binding base classes and ClassInfo for the given parameters.
     * This should be run after data binding exports the info files from layouts, and before
     * javac runs because it generates sources for javac.
     */
    @JvmStatic
    fun generateBaseClasses(options: GenerateBaseClassesOptions) {
        val layoutInfoFolder = prepareInput(arrayListOf(options.layoutInfoFolder))
        val classInfoOutFolder = Files.createTempDirectory("db-class-info-out").toFile()
        val args = LayoutInfoInput.Args(
                outOfDate = emptyList(),
                removed = emptyList(),
                infoFolder = layoutInfoFolder,
                dependencyClassesFolders = options.dependencyClassInfoFolders,
                artifactFolder = classInfoOutFolder,
                packageName = options.packageName,
                logFolder = Files.createTempDirectory("db-incremental-log").toFile(),
                incremental = false,
                useAndroidX = options.useAndroidX,
                enableViewBinding = options.enableViewBinding,
                enableDataBinding = options.enableDataBinding
        )
        val sourceFileWriter = if (options.zipSourceOutput) {
            ZipFileWriter(options.sourceFileOut)
        } else {
            options.sourceFileOut.mkdirs()
            DataBindingBuilder
                    .GradleFileWriter(options.sourceFileOut.absolutePath)
        }
        BaseDataBinder(LayoutInfoInput(args), null).generateAll(sourceFileWriter)
        if (options.zipSourceOutput) {
            (sourceFileWriter as ZipFileWriter).close()
        }
        // we need to zip class info because blaze likes it better
        ZipUtil.zip(classInfoOutFolder, options.classInfoOut)
    }

    /**
     * Creates a tmp folder w/ contents from each file. If the file is a zip, it is unzipped.
     */
    private fun prepareInput(inputs: List<File>): File {
        val outFolder = Files.createTempDirectory("db-class-info").toFile()
        inputs.forEach {
            if (it.isFile) {
                ZipUtil.unzip(it, outFolder)
            } else {
                throw IllegalArgumentException("${it.absolutePath} should've been a file a zip file." +
                        "It is not. Does it exist? ${it.exists()}")
            }
        }
        return outFolder
    }

    private fun createXmlProcessor(processXmlOptions: ProcessXmlOptions): LayoutXmlProcessor {
        val fileWriter = ExecFileWriter(processXmlOptions.resOutput)
        return LayoutXmlProcessor(processXmlOptions.appId, fileWriter, MyFileLookup(),
                processXmlOptions.useAndroidX)
    }

    internal class MyFileLookup : LayoutXmlProcessor.OriginalFileLookup {
        override fun getOriginalFileFor(file: File): File {
            return file
        }
    }

    internal class ZipFileWriter @Throws(FileNotFoundException::class)
    constructor(outZipFile: File) : JavaFileWriter() {
        private val zos: ZipOutputStream

        init {
            val fos = FileOutputStream(outZipFile)
            zos = ZipOutputStream(fos)
        }

        override fun writeToFile(canonicalName: String, contents: String) {
            doWrite(canonicalName.replace(".", "/") + ".java", contents)
        }

        override fun deleteFile(canonicalName: String) {
            throw UnsupportedOperationException("Cannot delete file")
        }

        override fun writeToFile(exactPath: File, contents: String) {
            doWrite(exactPath.name, contents)
        }

        private fun doWrite(entryPath: String, contents: String) {
            val entry = ZipEntry(entryPath)
            try {
                zos.putNextEntry(entry)
                zos.write(contents.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            } catch (t: Throwable) {
                L.e(t, "cannot write zip file. Filed on %s", entryPath)
            }
        }

        @Throws(IOException::class)
        fun close() {
            zos.close()
        }
    }

    internal class ExecFileWriter(private val base: File) : JavaFileWriter() {

        override fun writeToFile(canonicalName: String, contents: String) {
            val f = toFile(canonicalName)
            writeToFile(f, contents)
        }

        private fun toFile(canonicalName: String): File {
            val asPath = canonicalName.replace('.', '/')
            return File(base, "$asPath.java")
        }

        override fun deleteFile(canonicalName: String) {
            val file = toFile(canonicalName)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
