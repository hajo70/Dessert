package de.spricom.dessert.slicing;

import de.spricom.dessert.matching.NamePattern;
import de.spricom.dessert.resolve.ClassEntry;
import de.spricom.dessert.resolve.ClassResolver;
import de.spricom.dessert.resolve.ClassRoot;
import de.spricom.dessert.util.Predicate;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Classpath extends AbstractRootSlice {
    private static final Logger log = Logger.getLogger(Classpath.class.getName());
    private static ClassResolver defaultResolver;

    private final ClassResolver resolver;

    private final Map<String, Clazz> classes = new HashMap<String, Clazz>();

    private Slice classpathSlice;

    public Classpath() {
        this(getDefaultResolver());
    }

    public Classpath(ClassResolver resolver) {
        this.resolver = resolver;
        resolver.freeze();
    }

    private static ClassResolver getDefaultResolver() {
        if (defaultResolver == null) {
            try {
                defaultResolver = ClassResolver.ofClassPathAndBootClassPath();
            } catch (IOException ex) {
                throw new ResolveException("Unable to access classes on classpath.", ex);
            }
        }
        return defaultResolver;
    }

    Clazz asClazz(ClassEntry ce) {
        Clazz se = classes.get(ce.getClassname());
        if (se == null) {
            se = new Clazz(this, ce);
            classes.put(ce.getClassname(), se);
            return se;
        } else {
            Clazz alt = se.getAlternative(ce);
            return alt;
        }
    }

    public Clazz asClazz(String classname) {
        Clazz se = classes.get(classname);
        if (se == null) {
            se = resolveClazz(classname);
            if (se == null) {
                se = loadClass(classname);
            }
            if (se == null) {
                se = undefined(classname);
            }
            classes.put(classname, se);
        }
        return se;
    }

    public Clazz asClazz(Class<?> clazz) {
        Clazz se = classes.get(clazz.getName());
        if (se == null) {
            se = createClazz(clazz);
            classes.put(clazz.getName(), se);
        }
        return se;
    }

    private Clazz resolveClazz(String classname) {
        ClassEntry resolverEntry = resolver.getClassEntry(classname);
        if (resolverEntry == null) {
            return null;
        }
        return new Clazz(this, resolverEntry);
    }

    private Clazz createClazz(Class<?> clazz) {
        try {
            return new Clazz(this, clazz);
        } catch (IOException ex) {
            throw new ResolveException("Cannot analyze " + clazz, ex);
        }
    }

    private Clazz loadClass(String classname) {
        try {
            Class<?> clazz = Class.forName(classname);
            return new Clazz(this, clazz);
        } catch (ClassNotFoundException ex) {
            log.log(Level.FINE, "Cannot find " + classname, ex);
        } catch (IOException ex) {
            log.log(Level.WARNING, "Cannot analyze " + classname, ex);
        }
        return null;
    }

    private Clazz undefined(String classname) {
        return new Clazz(this, classname);
    }

    /**
     * Returns a slice of all duplicate .class files detected by the underlying {@link ClassResolver}.
     * Hence for each entry in this slice there are at least two .class files with the same classname but
     * different URL's.
     *
     * @return Maybe empty slice of all duplicate .class files
     */
    public ConcreteSlice duplicates() {
        Set<Clazz> sliceEntries = new HashSet<Clazz>();
        for (List<ClassEntry> alternatives : resolver.getDuplicates().values()) {
            for (ClassEntry alternative : alternatives) {
                sliceEntries.add(asClazz(alternative));
            }
        }
        return new ConcreteSlice(sliceEntries);
    }

    public Root rootOf(Class<?> clazz) {
        return rootOfClass(clazz.getName());
    }

    public Root rootOfClass(String classname) {
        ClassEntry cf = resolver.getClassEntry(classname);
        if (cf == null) {
            throw new IllegalArgumentException(classname + " not found within this context.");
        }
        return rootOf(cf.getPackage().getRoot());
    }

    public Root rootOf(final File rootFile) {
        return rootOf(getClassRoot(rootFile));
    }

    private Root rootOf(final ClassRoot root) {
        return new Root(root, this);
    }

    /**
     * Checks whether the corresponding root file has been added to the path.
     * It's not allowed to add root files to an existing slice context, because
     * that might change slices after they have been created.
     *
     * @param rootFile the classes directory or jar file to check
     * @return the root
     */
    private ClassRoot getClassRoot(File rootFile) {
        if (rootFile == null) {
            throw new NullPointerException("rootFile must not be null");
        }
        ClassRoot root = resolver.getRoot(rootFile);
        if (root == null) {
            throw new IllegalArgumentException(rootFile + " has not been registered with this context.");
        }
        return root;
    }

    public Slice sliceOf(Class<?>... classes) {
        if (classes.length == 0) {
            return Slices.EMPTY_SLICE;
        } else if (classes.length == 1) {
            return asClazz(classes[0]);
        }
        Set<Clazz> sliceEntries = new HashSet<Clazz>();
        for (Class<?> clazz : classes) {
            sliceEntries.add(asClazz(clazz));
        }
        return new ConcreteSlice(sliceEntries);
    }

    public Slice sliceOf(final String... classnames) {
        DerivedSlice derivedSlice = new DerivedSlice(new Predicate<Clazz>() {
            private final Set<String> names = new HashSet<String>(Arrays.asList(classnames));

            @Override
            public boolean test(Clazz sliceEntry) {
                return names.contains(sliceEntry.getName());
            }
        });
        return new DeferredSlice(derivedSlice, new ClazzResolver() {
            @Override
            public Set<Clazz> getClazzes() {
                Set<Clazz> sliceEntries = new HashSet<Clazz>();
                for (String name : classnames) {
                    sliceEntries.add(asClazz(name));
                }
                return sliceEntries;
            }
        });
    }


    @Override
    public Slice combine(Slice other) {
        throw new UnsupportedOperationException("Cannot combine anything with class path.");
    }

    @Override
    public Slice slice(String pattern) {
        NamePattern namePattern = NamePattern.of(pattern);
        NameResolver nameResolver = new NameResolver(this, namePattern, resolver);
        DerivedSlice derivedSlice = new DerivedSlice(namePattern);
        return new DeferredSlice(derivedSlice, nameResolver);
    }

    @Override
    public Slice slice(Predicate<Clazz> predicate) {
        return classpathSlice().slice(predicate);
    }

    @Override
    public boolean contains(Clazz clazz) {
        return classpathSlice().contains(clazz);
    }

    @Override
    public boolean isIterable() {
        return true;
    }

    @Override
    public Set<Clazz> getClazzes() {
        return classpathSlice().getClazzes();
    }

    private Slice classpathSlice() {
        if (classpathSlice == null) {
            classpathSlice = packageTreeOf("");
        }
        return classpathSlice;
    }
}