/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.XmlConfigFile
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeComponentInternal
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet
import org.gradle.util.CollectionUtils

/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
class DefaultVisualStudioProject extends AbstractBuildableModelElement implements VisualStudioProject {
    final VisualStudioProjectResolver projectResolver
    final String name
    final NativeComponent component
    final Map<NativeBinary, VisualStudioProjectConfiguration> configurations = [:]
    final DefaultConfigFile projectFile
    final DefaultConfigFile filtersFile

    DefaultVisualStudioProject(String name, NativeComponent component, FileResolver fileResolver, VisualStudioProjectResolver projectResolver, Instantiator instantiator) {
        this.name = name
        this.component = component
        this.projectResolver = projectResolver
        projectFile = instantiator.newInstance(DefaultConfigFile, fileResolver, "visualStudio/${name}.vcxproj" as String)
        filtersFile = instantiator.newInstance(DefaultConfigFile, fileResolver, "visualStudio/${name}.vcxproj.filters" as String)
    }

    String getUuid() {
        String projectPath = (component as ProjectNativeComponentInternal).projectPath
        String vsComponentPath = "${projectPath}:${name}"
        return '{' + UUID.nameUUIDFromBytes(vsComponentPath.bytes).toString().toUpperCase() + '}'
    }

    List<File> getSourceFiles() {
        def allSource = [] as Set
        component.source.each { LanguageSourceSet sourceSet ->
            if (!(sourceSet instanceof WindowsResourceSet)) {
                allSource.addAll sourceSet.source.files
            }
        }
        return allSource as List
    }

    List<File> getResourceFiles() {
        def allResources = [] as Set
        component.source.withType(WindowsResourceSet).each { WindowsResourceSet sourceSet ->
            allResources.addAll sourceSet.source.files
        }
        return allResources as List
    }

    List<File> getHeaderFiles() {
        def allHeaders = [] as Set
        component.source.withType(HeaderExportingSourceSet).each { HeaderExportingSourceSet sourceSet ->
            allHeaders.addAll sourceSet.exportedHeaders.files
        }
        return allHeaders as List
    }

    Set<DefaultVisualStudioProject> getProjectReferences() {
        def projects = [] as Set
        component.binaries.each { NativeBinary binary ->
            binary.libs.each { NativeDependencySet dependencySet ->
                if (dependencySet instanceof LibraryNativeDependencySet) {
                    LibraryBinary dependencyBinary = ((LibraryNativeDependencySet) dependencySet).getLibraryBinary()
                    projects << projectResolver.lookupProjectConfiguration(dependencyBinary).getProject()
               }
            }
        }
        return projects
    }

    List<VisualStudioProjectConfiguration> getConfigurations() {
        return CollectionUtils.toList(configurations.values())
    }

    void addConfiguration(NativeBinary nativeBinary, VisualStudioProjectConfiguration configuration) {
        configurations[nativeBinary] = configuration
    }

    VisualStudioProjectConfiguration getConfiguration(ProjectNativeBinary nativeBinary) {
        return configurations[nativeBinary]
    }

    public static class DefaultConfigFile implements XmlConfigFile {
        private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();
        private final FileResolver fileResolver
        private Object location

        DefaultConfigFile(FileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver
            this.location = defaultLocation
        }

        File getLocation() {
            return fileResolver.resolve(location)
        }

        void setLocation(Object location) {
            this.location = location
        }

        void withXml(Action<? super XmlProvider> action) {
            actions.add(action)
        }

        List<Action<? super XmlProvider>> getXmlActions() {
            return actions
        }
    }
}
