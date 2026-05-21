package com.contractsys.user;

import jakarta.persistence.*;

@Entity
@Table(name = "sys_permission")
public class SysPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String permissionCode;

    @Column(nullable = false, length = 40)
    private String permissionName;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(length = 200)
    private String url;

    @Column(length = 100)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

