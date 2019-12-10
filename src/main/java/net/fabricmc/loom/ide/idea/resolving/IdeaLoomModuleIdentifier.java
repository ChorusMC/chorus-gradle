package net.fabricmc.loom.ide.idea.resolving;

import java.util.Objects;

public class IdeaLoomModuleIdentifier {
    String name;
    String group;

    IdeaLoomModuleIdentifier(String name, String group) {
        this.name = name;
        this.group = group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IdeaLoomModuleIdentifier that = (IdeaLoomModuleIdentifier)o;

        if (!Objects.equals(name, that.name)) return false;
        if (!Objects.equals(group, that.group)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (group != null ? group.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return group + ":" + name;
    }
}
