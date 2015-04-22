/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;

public class DropAnnotationCache {
    public static void dropCache(Project project) {
        // 200ms
        BaseExternalAnnotationsManager instance =
                (BaseExternalAnnotationsManager) ExternalAnnotationsManager.getInstance(project);
        instance.dropCache();
        Map map = getField(instance, "annotationDataCache", BaseExternalAnnotationsManager.class);
        if (map != null) map.clear();

        // 50 ms
        ResolveCache.getInstance(project).clearCache(true);
        ResolveCache.getInstance(project).clearCache(false);

        // crash
        //FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
        //Map map1 = getField(fileTypeRegistry, "myExtensionsMap", fileTypeRegistry.getClass());
        //if (map1 != null) map1.clear();
    }

    @Nullable
    public static <T> T getField(Object obj, String fieldName, Class klass) {
        try {
            Field field = klass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        }
        catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
