/*
 * Copyright (c) 2019 Kurt Aaholst.  All Rights Reserved.
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

package uk.org.ngo.gradle

import groovy.xml.MarkupBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.regex.Pattern

/**
 * Plugin with task to extract the strings we use from slimserver and squeezeplay for the
 * languages we support and put them into the app source
 * <p>
 * In <code>build.gradle</code>:
 * <pre>
 *     apply plugin: 'uk.org.ngo.gradle.slimstrings'
 *
 *     slimstrings {
 *         files = 'list of files with strings from slimserver or squeezeplay'
 *         srings = 'list of tokens to translate'
 *     }
 * </pre>
 */
class SlimStringsPlugin implements Plugin<Project> {
    void apply(Project project) {
        /**
         * Insert/update strings from slimserver and squeezeplay.
         */
        project.extensions.create('slimstrings', SlimStringsPluginExtension)
        project.task('updateSlimStrings') {
            doLast {
                // Find the languages in the app source
                def languages = new HashMap<String, File>()
                def tree = project.fileTree(dir: 'src/main/res', include: 'values/strings.xml')
                tree += project.fileTree(dir: 'src/main/res', include: 'values-??/strings.xml')
                tree.each { file ->
                    def name = file.parentFile.name
                    def language = (name == 'values' ? 'en' : name[-2..-1]).toUpperCase()
                    def outFile = new File(file.parentFile, "serverstrings.xml");
                    languages.put(language, outFile)
                }


                // Find the translations from slimserver and squeezeplay
                def pattern = Pattern.compile('^\t+([^%s]+)\t+(.+)')
                def translations = new HashMap<String, Map<String, String>>()
                project.slimstrings.files.each { filename ->
                    def token = null
                    new File(filename).getText().eachLine { line ->
                        if (line) {
                            if (Character.isUpperCase(line.toCharacter())) {
                                token = (project.slimstrings.strings.contains(line)) ? line : null
                            } else {
                                if (token) {
                                    line.eachMatch pattern, { s, language, translation ->
                                        if (translation && languages.containsKey(language)) {
                                            def slimStrings = translations[language]
                                            if (!slimStrings) {
                                                translations.put(language, slimStrings = new HashMap<>())
                                            }
                                            slimStrings.put(token, translation)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Update translations in the app
                languages.each {language, file ->
                    def xmlWriter = new FileWriter(file)
                    def xmlMarkup = new MarkupBuilder(xmlWriter)
                    xmlMarkup.resources( 'xmlns:tools':'http://schemas.android.com/tools', 'tools:ignore':'MissingTranslation') {
                        mkp.comment('\nAutomatically generated file. DO NOT MODIFY\n')
                        translations[language].each { name, translation ->
                            string(name: name, translation.replace("'", "\\'").replace("...", "…"))

                        }
                    }
                }
            }
        }
    }
}

class SlimStringsPluginExtension {
    public String[] files
    public String[] strings
}
