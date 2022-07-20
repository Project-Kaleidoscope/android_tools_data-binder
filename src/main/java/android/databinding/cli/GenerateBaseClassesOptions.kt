/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.databinding.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import java.io.File

/**
 * This class represents the list of options that can be passed from the command line into
 * [AndroidDataBinding] runnable. In return, we generate the base classes and class info
 * information, similar to what DataBindingGenBaseClassesTask does on the gradle side.
 */
@Parameters(commandDescription = "Generate the base classes and class info from layout files")
class GenerateBaseClassesOptions {
    /**
     * The folder containing the layout info files. Can be a zip file as well.
     */
    @Parameter(
        names = ["-layoutInfoFiles"], required = true,
        converter = FileConverter::class,
        description = "The zip file or folder " +
                "containing the layout info files."
    )
    lateinit var layoutInfoFolder: File

    /**
     * The folder containing the class info files from dependencies. That folder should contain
     * all class info files that were exported by this class for dependency modules
     * (see outClassInfo)
     */
    @Parameter(
        names = ["-dependencyClassInfoList"], required = false,
        converter = FileConverter::class,
        description = "The list" +
                " of class info files that were extracted from dependencies. That is basically " +
                "this task's output when it was run for the dependency"
    )
    var dependencyClassInfoFolders: List<File> = emptyList()

    /**
     * Package name of the current module (the same value used for R classes)
     */
    @Parameter(
        names = ["-package"],
        required = true,
        description = "The package name of the application. This should be the same package that " +
                "R file uses."
    )
    lateinit var packageName: String

    /**
     * Output file where I should write the ClassInfo file. That file should be available to my
     * dependants.
     */
    @Parameter(
        names = ["-classInfoOut"], required = true, description = "The output folder " +
                "where this task will generate the class info file. That metadata should be " +
                "passed down to dependants"
    )
    lateinit var classInfoOut: File

    /**
     * Folder to output generated source files.
     */
    @Parameter(
        names = ["-sourceOut"], required = true, description = "The location of the" +
                " folder or zip file where this task should generate java sources."
    )
    lateinit var sourceFileOut: File

    /**
     * True if I should zip the output sources that I generated.
     */
    @Parameter(
        names = ["-zipSourceOutput"], required = false, description = "Specifies " +
                "whether the source output should be exported as 1 zip file instead of a folder.",
            arity = 1
    )
    var zipSourceOutput: Boolean = false

    /**
     * True if Data Binding should generate code that uses androidX.
     */
    @Parameter(
            names = ["-useAndroidX"], required = false, description = "Specifies " +
            "whether data binding should use androidX packages or not",
            arity = 1
    )
    var useAndroidX: Boolean = true

    @Parameter(
        names = ["-enableViewBinding"], required = false,
        arity = 1
    )
    var enableViewBinding: Boolean = true

    @Parameter(
        names = ["-enableDataBinding"], required = false,
        arity = 1
    )
    var enableDataBinding: Boolean = true
}
