package classloader;

public class Util {
    public static String classToQualified(String classFileName) {
        return classFileName.replace("/", ".").replace(".class", "");
    }

    public static String qualifiedToClass(String qualifiedName) {
        return qualifiedName.replace(".", "/") + ".class";
    }

    public static String descriptorToQualified(String binaryName) {
        if (binaryName.startsWith("L") && binaryName.endsWith(";")) {
            return binaryName
                    .substring(1)
                    .replace(";", "")
                    .replace("/", ".")
                    .replace("$", ".");
        }
        return "";
    }
}
