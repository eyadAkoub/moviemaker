package com.akirkpatrick.mm.model;

import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@NamedQuery(name="Account.findByUsername", query="select a from Account a where a.username=:username")
@Entity
public class Account {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    private String username;
    private String md5Password;
    @OneToMany
    private List<Project> projects=new ArrayList<Project>();

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setMd5Password(String md5Password) {
        this.md5Password = md5Password;
    }

    public String getMd5Password() {
        return md5Password;
    }

    public Project addProject(Project project) {
        projects.add(project);
        return project;
    }

    public List<Project> getProjects() {
        return projects;
    }
}
