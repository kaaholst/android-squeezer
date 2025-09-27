/*
 * Copyright (c) 2015 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.gradle;

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * Plugin with tasks for regenerating what's new and NEWS files from the contents of the
 * XML changelog.
 * <p>
 * In <code>build.gradle</code>:
 * <pre>
 *     apply plugin: 'uk.org.ngo.gradle.whatsnew'
 *
 *     whatsnew {
 *         changelogPath = 'path/to/changelog_master.xml'
 *         newsPath = 'path/to/NEWS'
 *         whatsnewPath = 'path/to/whatsnew'
 *     }
 * </pre>
 */
class WhatsNewPlugin implements Plugin<Project> {
    void apply(Project project) {
        /**
         * Generates the whatsnew file from scratch from the contents of the changelog.
         */
        project.extensions.create('whatsnew', WhatsNewPluginExtension)
        project.task('generateWhatsNew') {
            doLast {
                def changelog = new XmlSlurper().parseText(
                        new File(project.whatsnew.changelogPath).getText('UTF-8'))

                String content = ''
                changelog.release[0].change.each { change ->
                    content += '&bull; ' + change.text().replaceAll('\n', ' ').replaceAll(' +', ' ').trim() + '\n\n'
                }

                new File(project.whatsnew.whatsnewPath).setText(content.trim(), 'UTF-8')
            }
        }

        /**
         * Generates the NEWS file from scratch from the contents of the changelog.
         */
        project.task('generateNews') {
            doLast {
                def changeLog = new XmlSlurper().parseText(
                        new File(project.whatsnew.changelogPath).getText('UTF-8'))

                String content = '---\n' +
                        'description: Changelog\n' +
                        'permalink: /changelog/\n' +
                        '---\n\n'
                changeLog.release.each { release ->
                    content += release.@version.text() + '\n'
                    content += ('=' * release.@version.text().size()) + '\n\n'
                    release.change.each { change ->
                        String filledText = fill(
                                change.text()
                                        .replaceAll('\n', ' ')
                                        .replaceAll(' +', ' ')
                                        .replaceAll('\u200B', '')  // Collapse zero-width space
                                        .trim(),
                                78, '    ').trim()
                        content += '*   ' + filledText + '\n\n'
                    }
                    content += '\n'
                }

                new File(project.whatsnew.newsPath).setText(content.trim(), 'UTF-8')
            }
        }
    }

    /**
     * Rearranges text in to a column of a certain width, with an optional string preceding
     * each line.
     *
     * @param text the text to rearrange
     * @param width the maximum width of the column
     * @param prefix the string to prepend to each line.
     * @return the rearranged text
     */
    def static fill(text, width=72, prefix='') {
        width = width - prefix.size()
        def out = []
        List words = text.replaceAll("\n", " ").split(" ")
        while (words) {
            def line = ''
            while (words) {
                if (line.size() + words[0].size() + 1 > width) {
                    break
                }
                if (line) {
                    line += ' '
                }
                line += words[0]
                words = words.tail()
            }
            out += prefix + line
        }
        out.join("\n")
    }
}

class WhatsNewPluginExtension {
    String changelogPath
    String newsPath
    String whatsnewPath
}