/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.gradle.licenser

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths

class LicenserPluginFunctionalTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    static final def standardArguments = ["--warning-mode", "fail", "--stacktrace"].asImmutable()

    static final def configurationCacheTestMatrix = [
            // TODO: restore android plugin once versions that support configuration cache are available
            /* gradleVersion | androidVersion | extraArgs */
            [ "6.8.3",         null,            ["--configuration-cache"] ],
            [ "6.9",           null,            ["--configuration-cache"] ],
            [ "7.0.2",         null,            ["--configuration-cache"] ],
            [ "7.1",           null,            ["--configuration-cache"] ],
    ].asImmutable()

    static final def testMatrix = ([
            /* gradleVersion | androidVersion | extraArgs */
            [ "6.8.3",         "4.1.0",         [] ],
    ] + configurationCacheTestMatrix).asImmutable()

    GradleRunner runner(File projectDir, gradleVersion, args) {
        return GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withGradleVersion(gradleVersion)
                .withArguments(standardArguments + args)
                .withProjectDir(projectDir)
    }

    @Unroll
    def "can run licenseCheck task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
            }
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseCheck").build()

        then:
        result.task(":checkLicenses").outcome == TaskOutcome.UP_TO_DATE
        result.task(":licenseCheck").outcome == TaskOutcome.UP_TO_DATE

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "skips existing headers in checkLicenses task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << "New copyright header"
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('java')
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file('header.txt')
                skipExistingHeaders = true
            }
        """.stripIndent()
        new File(sourceDir, "MyClass.java") << """
            /*
             * Existing copyright header
             */
            
            package com.example;
            
            class MyClass {}
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "checkLicenses").build()

        then:
        result.task(":checkLicenses").outcome == TaskOutcome.SUCCESS

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "allow spaces in new line at checkLicenses task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << "Copyright header"
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('java')
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file('header.txt')
            }
        """.stripIndent()

        new File(sourceDir, "MyClass.java") << String.join("\n",
            "/*",
            " * Copyright header",
            " */",
            "   ", // allow spaces
            "package com.example;",
            "",
            "class MyClass {}",
            ""
        )

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "checkLicenses").build()

        then:
        result.task(":checkLicenses").outcome == TaskOutcome.SUCCESS

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "skips existing headers in updateLicenses task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << "New copyright header"
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('java')
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file('header.txt')
                skipExistingHeaders = true
            }
        """.stripIndent()
        def sourceFileContent = """\
            /*
             * Existing copyright header
             */
            
            package com.example;
            
            class MyClass {}
        """.stripIndent()
        def sourceFile = new File(sourceDir, "MyClass.java") << sourceFileContent

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "updateLicenses").build()

        then:
        result.task(":updateLicenses").outcome == TaskOutcome.UP_TO_DATE
        sourceFile.text == sourceFileContent

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "updates invalid headers in updateLicenses task when skipExistingHeaders=true (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << "New copyright header"
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('java')
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file('header.txt')
                skipExistingHeaders = true
            }
        """.stripIndent()
        def sourceFileContent = """\
            //
            // Existing copyright header
            //
            
            package com.example;
            
            class MyClass {}
        """.stripIndent()
        def sourceFile = new File(sourceDir, "MyClass.java") << sourceFileContent

        when:
        def runner = runner(projectDir, gradleVersion, extraArgs + "updateLicenses")
        runner.debug = true
        def result = runner.build()

        then:
        result.task(":updateLicenses").outcome == TaskOutcome.SUCCESS
        sourceFile.text == """\
            /*
             * New copyright header
             */
            
            //
            // Existing copyright header
            //
            
            package com.example;
            
            class MyClass {}
        """.stripIndent()

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "can run licenseFormat task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
            }
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseFormat").build()

        then:
        result.output.contains("Task :updateLicenses UP-TO-DATE")
        result.output.contains("Task :licenseFormat UP-TO-DATE")
        result.task(":updateLicenses").outcome == TaskOutcome.UP_TO_DATE
        result.task(":licenseFormat").outcome == TaskOutcome.UP_TO_DATE

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "license format does not update valid files"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "LICENSE.txt") << """
            MIT License
            
            Copyright (c) 2020-present Cloudogu GmbH and Contributors
            
            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction, including without limitation the rights
            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
            copies of the Software, and to permit persons to whom the Software is
            furnished to do so, subject to the following conditions:
            
            The above copyright notice and this permission notice shall be included in all
            copies or substantial portions of the Software.
            
            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
            SOFTWARE.
        """.stripIndent().trim()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            /*
             * MIT License
             *
             * Copyright (c) 2020-present Cloudogu GmbH and Contributors
             *
             * Permission is hereby granted, free of charge, to any person obtaining a copy
             * of this software and associated documentation files (the "Software"), to deal
             * in the Software without restriction, including without limitation the rights
             * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
             * copies of the Software, and to permit persons to whom the Software is
             * furnished to do so, subject to the following conditions:
             *
             * The above copyright notice and this permission notice shall be included in all
             * copies or substantial portions of the Software.
             *
             * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
             * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
             * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
             * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
             * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
             * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
             * SOFTWARE.
             */
            
            
            plugins {
                id('org.scm-manager.license')
            }
            
            license {
                header = project.file("LICENSE.txt")
                ignoreNewLine = true
                newLine = true
                tasks {
                    build {
                        files.from("build.gradle")
                    }
                }
            }
        """.stripIndent().trim()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseFormat").build()

        then:
        result.output.contains("Task :updateLicenses UP-TO-DATE")
        result.output.contains("Task :licenseFormat UP-TO-DATE")
        result.task(":updateLicenses").outcome == TaskOutcome.UP_TO_DATE
        result.task(":licenseFormat").outcome == TaskOutcome.UP_TO_DATE

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "supports custom source sets task (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
                id('java')
            }
            sourceSets {
                mySourceSet {}
            }
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseCheck").build()

        then:
        result.task(":checkLicenseMySourceSet").outcome == TaskOutcome.NO_SOURCE

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "supports custom style (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourcesDir = new File(projectDir, "sources")
        sourcesDir.mkdirs()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "header.txt") << "Copyright header"
        def sourceFile = new File(sourcesDir, "source.c") << "TEST"
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file("header.txt")
                newLine = false
                style {
                    c = 'BLOCK_COMMENT'
                }
                tasks {
                    sources {
                        files.from("sources")
                        include("**/*.c")
                    }
                }
            }
        """.stripIndent()

        when:
        def runner = runner(projectDir, gradleVersion, extraArgs + "updateLicenses")
        runner.debug = true
        def result = runner.build()

        then:
        result.task(":updateLicenseCustomSources").outcome == TaskOutcome.SUCCESS
        sourceFile.text == """\
            /*
             * Copyright header
             */
            TEST
            """.stripIndent()

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "support mapping without extension (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourcesDir = new File(projectDir, "sources")
        sourcesDir.mkdirs()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "header.txt") << "Copyright header"
        def sourceFile = new File(sourcesDir, "Specialfile") << "TEST"
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
            }
            
            license {
                lineEnding = '\\n'
                header = project.file("header.txt")
                newLine = false
                style {
                    Specialfile = 'HASH'
                }
                tasks {
                    sources {
                        files.from("sources")
                    }
                }
            }
        """.stripIndent()

        when:
        def runner = runner(projectDir, gradleVersion, extraArgs + "updateLicenses")
        runner.debug = true
        def result = runner.build()

        then:
        result.task(":updateLicenseCustomSources").outcome == TaskOutcome.SUCCESS
        sourceFile.text == """\
            #
            # Copyright header
            #
            TEST
            """.stripIndent()

        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }

    @Unroll
    def "license formatting configuration is configuration-cacheable (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('org.scm-manager.license')
            }
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseFormat").build()

        then:
        result.output.contains("Configuration cache entry stored")

        when:
        def resultCached = runner(projectDir, gradleVersion, extraArgs + "licenseFormat").build()

        then:
        resultCached.output.contains("Reusing configuration cache")
        resultCached.task(":licenseFormat").outcome == TaskOutcome.UP_TO_DATE


        where:
        [gradleVersion, _, extraArgs] << configurationCacheTestMatrix
    }

    @Unroll
    def "supports Android source sets (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            buildscript {
                repositories {
                    google()
                }
            
                dependencies {
                    classpath 'com.android.tools.build:gradle:$androidVersion'
                }
            }

            plugins {
                id('org.scm-manager.license')
            }
            apply plugin: 'com.android.application'
            
            android {
                compileSdkVersion 30
                buildToolsVersion '30.0.1'
                defaultConfig {
                    applicationId 'org.gradle.samples'
                    minSdkVersion 16
                    targetSdkVersion 30
                    versionCode 1
                    versionName '1.0'
                    testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
                }
            }
        """.stripIndent()

        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "licenseCheck").build()

        then:
        result.task(":checkLicenseAndroidMain").outcome == TaskOutcome.NO_SOURCE
        result.task(":checkLicenseAndroidRelease").outcome == TaskOutcome.NO_SOURCE
        result.task(":checkLicenseAndroidTest").outcome == TaskOutcome.NO_SOURCE

        where:
        [gradleVersion, androidVersion, extraArgs] << testMatrix.findAll { it[1 /* androidVersion */] != null }
    }

    @Unroll
    def "supports Kotlin buildscripts (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << 'New copyright header for ${project}'
        new File(projectDir, "settings.gradle.kts") << ""
        new File(projectDir, "build.gradle.kts") << """
            plugins {
                java
                id("org.scm-manager.license")
            }
            
            license {
                lineEnding("\\n")
                header("header.txt")
                properties {
                    this["project"] = "AirhornPowered"
                }
            }
        """.stripIndent()
        def sourceFileContent = """\
            package com.example;
            
            class MyClass {}
        """.stripIndent()
        def sourceFile = new File(sourceDir, "MyClass.java") << sourceFileContent

        when:
        def runner = runner(projectDir, gradleVersion, extraArgs + "updateLicenses")
        runner.debug = true
        def result = runner.build()

        then:
        result.task(":updateLicenses").outcome == TaskOutcome.SUCCESS
        sourceFile.text == """\
            /*
             * New copyright header for AirhornPowered
             */
            
            package com.example;
            
            class MyClass {}
        """.stripIndent()

        where:
        [gradleVersion, _, extraArgs] << testMatrix

    }

    @Unroll
    def "ignore new line (gradle #gradleVersion)"() {
        given:
        def projectDir = temporaryFolder.newFolder()
        def sourceDir = projectDir.toPath().resolve(Paths.get("src", "main", "java", "com", "example")).toFile()
        sourceDir.mkdirs()
        new File(projectDir, "header.txt") << "Copyright header"
        new File(projectDir, "settings.gradle") << ""
        new File(projectDir, "build.gradle") << """
            plugins {
                id('java')
                id('org.scm-manager.license')
            }

            license {
                lineEnding = '\\n'
                header = project.file('header.txt')
                ignoreNewLine = true
            }
        """.stripIndent()
        new File(sourceDir, "One.java") << String.join("\n",
                "/*",
                " * Copyright header",
                " */",
                "   ",
                "package com.example;",
                "",
                "class One {}",
                ""
        )
        new File(sourceDir, "Two.java") << String.join("\n",
                "/*",
                " * Copyright header",
                " */",
                "",
                "package com.example;",
                "",
                "class Two {}",
                ""
        )
        new File(sourceDir, "Three.java") << String.join("\n",
                "/*",
                " * Copyright header",
                " */",
                "package com.example;",
                "",
                "class Three {}",
                ""
        )
        when:
        def result = runner(projectDir, gradleVersion, extraArgs + "checkLicenses").build()
        then:
        result.task(":checkLicenses").outcome == TaskOutcome.SUCCESS
        where:
        [gradleVersion, _, extraArgs] << testMatrix
    }
}
