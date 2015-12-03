package battlecode.engine.instrumenter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for IndividualClassLoader.
 *
 * @author james
 */
public class IndividualClassLoaderTest {
    @Before
    public void resetIndividualClassLoader() {
        IndividualClassLoader.reset();
    }

    // Should always give the same result for loadClass(string)
    @Test
    public void testLoadsClassesRepeatedly() throws ClassNotFoundException {
        final IndividualClassLoader l
                = new IndividualClassLoader("testplayerempty", false);

        final List<String> classNames = new ArrayList<>();

        classNames.add("java.lang.Object");

        classNames.add("testplayerempty.Helper");

        classNames.addAll(IndividualClassLoader.alwaysRedefine);

        final List<Class<?>> loadedClasses = new ArrayList<>();

        // Reloading consecutively should work
        for (String className : classNames) {
            final Class<?> loadedClass = l.loadClass(className);
            final Class<?> reLoadedClass = l.loadClass(className);

            assertEquals(loadedClass, reLoadedClass);

            loadedClasses.add(loadedClass);
        }

        // Reloading in a different order should work
        for (int i = 0; i < classNames.size(); i++) {
            assertEquals(l.loadClass(classNames.get(i)), loadedClasses.get(i));
        }
    }

    // Should reload player classes between instances.
    @Test
    public void testReloadsPlayerClasses() throws ClassNotFoundException {
        final IndividualClassLoader l1
                = new IndividualClassLoader("testplayerempty", false);
        final IndividualClassLoader l2
                = new IndividualClassLoader("testplayerempty", false);

        assertNotEquals(
                l1.loadClass("testplayerempty.Helper"),
                l2.loadClass("testplayerempty.Helper")
        );

        assertNotEquals(
                l1.loadClass("testplayerempty.RobotPlayer"),
                l2.loadClass("testplayerempty.RobotPlayer")
        );
    }

    // Should reload always-reloadable classes between instances.
    @Test
    public void testReloadsAlwaysReloadClasses() throws ClassNotFoundException {
        final IndividualClassLoader l1
                = new IndividualClassLoader("testplayerempty", false);
        final IndividualClassLoader l2
                = new IndividualClassLoader("testplayerempty", false);

        for (String alwaysRedefine : IndividualClassLoader.alwaysRedefine) {
            assertNotEquals(
                l1.loadClass(alwaysRedefine),
                l2.loadClass(alwaysRedefine)
            );
        }
    }

    @Test
    public void testLoadsSystemClasses() throws ClassNotFoundException {

    }
}
