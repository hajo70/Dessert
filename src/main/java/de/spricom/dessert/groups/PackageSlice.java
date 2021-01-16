package de.spricom.dessert.groups;

import de.spricom.dessert.slicing.Clazz;

import java.util.Collections;
import java.util.Set;

/**
 * A slice represents (subset of) a single Java package for one concrete root.
 * It's elements are .class files contained in the package. A root is either a
 * directory in the file-system or a jar file. Hence each elements of a slice is
 * unique. (There may be two classes with the same name on the classpath, but
 * with the combination of classname and root given by a PackageSlice the class is
 * uniquely defined.) A slice may represent a subset of the .class files in a
 * package, for example all interfaces, all classes complying some naming
 * convention, all classes implementing some interfaces, all inner classes etc.
 */
public class PackageSlice extends PartSlice {

    PackageSlice(String packageName, Set<Clazz> entries) {
        super(entries, packageName);
    }

    public String getPackageName() {
        return getPartKey();
    }

    public String getParentPackageName() {
        String packageName = getPackageName();
        int pos = packageName.lastIndexOf('.');
        if (pos == -1) {
            return "";
        }
        return packageName.substring(0, pos);
    }

    public PackageSlice getParentPackage(SliceGroup<PackageSlice> group) {
        PackageSlice parentPackage = group.getByPartKey(getParentPackageName());
        if (parentPackage == null) {
            parentPackage = new PackageSlice(getParentPackageName(), Collections.<Clazz>emptySet());
        }
        return parentPackage;
    }
}
