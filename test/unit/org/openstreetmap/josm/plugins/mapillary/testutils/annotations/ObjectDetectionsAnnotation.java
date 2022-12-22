// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.testutils.annotations;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.gui.MainApplicationTest;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetsTest;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.ObjectDetections;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.HTTP;
import org.xml.sax.SAXException;

/**
 * Annotation for ObjectDetections (ensures they have the appropriate presets)
 *
 * @author Taylor Smock
 */
@Documented
@HTTP
@ExtendWith(ObjectDetectionsAnnotation.ObjectDetectionsExtension.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@BasicPreferences
public @interface ObjectDetectionsAnnotation {
    class ObjectDetectionsExtension implements AfterAllCallback, BeforeAllCallback {

        @Override
        public void afterAll(ExtensionContext context) throws IOException, SAXException {
            this.beforeAll(context);
        }

        @Override
        public void beforeAll(ExtensionContext context) throws IOException, SAXException {
            // TODO replace with @Presets dependency
            MainApplicationTest.setTaggingPresets(TaggingPresetsTest.initFromDefaultPresets());
            ObjectDetections.updatePresets();
        }
    }
}
