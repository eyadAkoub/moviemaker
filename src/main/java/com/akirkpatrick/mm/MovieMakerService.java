package com.akirkpatrick.mm;

import com.akirkpatrick.mm.model.Account;
import com.akirkpatrick.mm.model.Project;
import com.akirkpatrick.mm.model.ProjectInfo;
import com.sun.jersey.core.util.Base64;
import org.hibernate.annotations.common.util.impl.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.FileCopyUtils;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.logging.LogManager;
import java.util.logging.Logger;

@Component
public class MovieMakerService {
    private static final Logger log = Logger.getLogger(MovieMakerService.class.getName());

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        final InputStream inputStream = getClass().getResourceAsStream("/logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(inputStream);
        } catch (IOException e) {
            System.err.println("Could not load logging.properties");
            e.printStackTrace(System.err);

            Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        // ensure admin account exists
        Account admin = findAccount("admin");
        if (admin == null) {
            log.info("Created admin account");
            createAccount("admin", "r1ngst3ad");
        }
    }

    @Transactional
    public String store(String base64data, Account account, Long projectId) {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = Base64.decode(base64data);
        try {
            File file = FileHelper.toFile(uuid);
            File dir = new File(file.getParent());
            if (!dir.exists()) {
                dir.mkdir();
            }

            FileCopyUtils.copy(bytes, file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Project project = getProject(account, projectId);
        project.addFrame(uuid.toString());

        return uuid.toString();
    }

    @Transactional(readOnly = true)
    public Project getProject(Account account, Long projectId) {
        return safeGetProject(account, projectId);
    }

    private Project safeGetProject(Account account, Long projectId) {
        Project project = em.find(Project.class, projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found with id: " + projectId);
        }
        if (!account.getId().equals(project.getAccount().getId())) {
            throw new IllegalArgumentException("Attempting to access a project not owned by account!");
        }
        return project;
    }

    @Transactional
    public void deleteProject(Account account, Long projectId) {
        Project project = safeGetProject(account, projectId);
        deleteProject(project);
    }

    @Transactional
    public void deleteFrame(Account account, Long projectId, Integer frameNum) {
        Project project = getProject(account, projectId);
        project.removeFrame(frameNum);
    }

    @Transactional(readOnly = true)
    public Account authenticate(String username, String password) {
        Query query = em.createNamedQuery("Account.findByUsernameAndPassword");
        String digest = DigestUtils.md5DigestAsHex(password.getBytes());
        query.setParameter("username", username);
        query.setParameter("password", digest);
        return (Account) query.getSingleResult();
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return em.createNamedQuery("Account.findAll", Account.class).getResultList();
    }

    @Transactional(readOnly = true)
    public Account findAccount(String username) {
        Query query = em.createNamedQuery("Account.findByUsername");
        query.setParameter("username", username);
        List resultList = query.getResultList();
        if (resultList.size() == 0) {
            return null;
        }
        return (Account) resultList.get(0);
    }

    @Transactional(readOnly = true)
    public Account findAccount(Long accountId) {
        return em.find(Account.class, accountId);
    }

    @Transactional
    public Account createAccount(String username, String password) {
        String digest = DigestUtils.md5DigestAsHex(password.getBytes());
        Account account = new Account();
        account.setUsername(username);
        account.setMd5Password(digest);
        em.persist(account);
        return account;
    }

    @Transactional
    public Project addProject(Account account, String name) {
        Project project = new Project();
        project.setName(name);
        project.setAccount(account);
        em.persist(project);
        account.getProjects().add(project);
        return project;
    }

    @Transactional
    public Project updateProjectInfo(ProjectInfo projectInfo) {
        Project project = em.find(Project.class, projectInfo.getId());
        project.setName(projectInfo.getName());
        project.setFps(projectInfo.getFps());
        return project;
    }

    @Transactional(readOnly = true)
    public boolean isAccount(String username) {
        return em.createNamedQuery("Account.findByUsername")
                .setParameter("username", username)
                .getResultList().size() == 1;
    }

    @Transactional(readOnly = true)
    public Project findProject(Long projectId) {
        return em.find(Project.class, projectId);
    }

    //    @Scheduled(fixedDelay=10000000)
    @Transactional
    public void cleanup() {
        // find projects that haven't been modified for more than 24 hrs
        Calendar calendar = Calendar.getInstance();
        // TODO: H: modify age before re-enabling cleanup!
        calendar.roll(Calendar.MINUTE, false);

        System.out.println("cleaning up expired projects...");

        List<Project> list = em.createNamedQuery("Project.findExpired", Project.class)
                .setParameter("cutoff", calendar)
                .getResultList();

        for (Project p : list) {
            deleteProject(p);
        }
        System.out.println("cleanup done");
    }

    private void deleteProject(Project p) {
        System.out.println("deleting project: " + p.getAccount().getUsername() + ":" + p.getName());
        // delete all the frames
        List<String> frames = p.getFrames();
        for (String f : frames) {
            System.out.println("+ deleting frame: " + f);
            FileHelper.delete(f);
        }
        // delete the project
        em.remove(p);
    }

}
