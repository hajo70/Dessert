package de.spricom.dessert.test.slicing;

import de.spricom.dessert.resolve.ClassResolver;
import de.spricom.dessert.slicing.Slice;
import de.spricom.dessert.slicing.SliceContext;
import de.spricom.dessert.slicing.SliceEntry;
import de.spricom.dessert.test.resolve.FakeClassEntry;
import de.spricom.dessert.test.resolve.FakeRoot;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

public class SliceEntryTest {

    @Test
    public void testThisClass() throws MalformedURLException {
        SliceContext sc = new SliceContext();
        Slice slice = sc.sliceOf(SliceEntryTest.class.getName());
        Set<SliceEntry> entries = slice.getSliceEntries();
        assertThat(entries).hasSize(1);
        SliceEntry entry = entries.iterator().next();
        assertThat(entry.getAlternatives()).hasSize(1);

        assertThat(entry.getClassName()).isEqualTo(getClass().getName());
        assertThat(entry.getClassFile().getThisClass()).isEqualTo(getClass().getName());
        assertThat(entry.getClazz()).isSameAs(getClass());
        assertThat(entry.getPackageName()).isEqualTo(getClass().getPackage().getName());
        
        assertThat(entry.getSuperclass().getClazz()).isSameAs(Object.class);
        assertThat(entry.getImplementedInterfaces()).isEmpty();
        assertThat(entry.getURI().toURL()).isEqualTo(getClass().getResource(getClass().getSimpleName() + ".class"));
        assertThat(new File(entry.getURI().toURL().getPath()).getAbsolutePath()).startsWith(entry.getRootFile().getAbsolutePath());
    }
    
    @Test
    public void testCreateSliceEntryWithAlternative() throws IOException {
        ClassResolver resolver = new ClassResolver();
        FakeRoot root1 = new FakeRoot(new File("/root1"));
        resolver.addRoot(root1);
        FakeRoot root2 = new FakeRoot(new File("/root2"));
        resolver.addRoot(root2);

        String fakeClassName = FakeClassEntry.class.getName();
        root1.add(fakeClassName);
        root2.add(fakeClassName);

        SliceContext sc = new SliceContext(resolver);
        Slice slice = sc.packageTreeOf("de.spricom.dessert");
        Set<SliceEntry> entries = slice.getSliceEntries();
        assertThat(entries).hasSize(1);
        SliceEntry entry = entries.iterator().next();
        assertThat(entry.getAlternatives()).hasSize(2);

        Slice duplicates = sc.duplicates();
        // For each classname there is only one entry, even if there are alternatives.
        assertThat(duplicates.getSliceEntries()).hasSize(1);
        // The flyweight pattern must ensure there is only one SliceEntry instance.
        assertThat(duplicates.getSliceEntries().iterator().next()).isSameAs(entry);
    }
}
