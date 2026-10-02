package com.werewolf.role;

public class Role {

    private final String name;
    private String displayName;
    private String camp;
    private String description;

    private String skill1Name;
    private String skill2Name;
    private String skill3Name;
    private String skill4Name;
    private String passiveName;

    public Role(String name) {
        this.name = name;
        this.displayName = name;
        this.camp = "好人阵营";
        this.description = "";
        this.skill1Name = "";
        this.skill2Name = "";
        this.skill3Name = "";
        this.passiveName = "";
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getCamp() { return camp; }
    public void setCamp(String camp) { this.camp = camp; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSkill1Name() { return skill1Name; }
    public void setSkill1Name(String name) { this.skill1Name = name; }
    public String getSkill2Name() { return skill2Name; }
    public void setSkill2Name(String name) { this.skill2Name = name; }
    public String getSkill3Name() { return skill3Name; }
    public void setSkill3Name(String name) { this.skill3Name = name; }
    public String getSkill4Name() { return skill4Name; }
    public void setSkill4Name(String name) { this.skill4Name = name; }
    public String getPassiveSkillName() { return passiveName; }
    public void setPassiveSkillName(String name) { this.passiveName = name; }

    public boolean hasSkill1() { return skill1Name != null && !skill1Name.isEmpty() && !skill1Name.equals("无"); }
    public boolean hasSkill2() { return skill2Name != null && !skill2Name.isEmpty() && !skill2Name.equals("无"); }
    public boolean hasSkill3() { return skill3Name != null && !skill3Name.isEmpty() && !skill3Name.equals("无"); }
    public boolean hasSkill4() { return skill4Name != null && !skill4Name.isEmpty() && !skill4Name.equals("无"); }
    public boolean hasPassiveSkill() { return passiveName != null && !passiveName.isEmpty() && !passiveName.equals("无"); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return name.equals(role.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
