package pl.bsiwallet.bsitest.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import pl.bsiwallet.bsitest.entities.Password;
import pl.bsiwallet.bsitest.interfaces.PasswordRepository;
import pl.bsiwallet.bsitest.utils.Access;
import pl.bsiwallet.bsitest.utils.SecurityUtils;
import pl.bsiwallet.bsitest.utils.UserSession;
import pl.bsiwallet.bsitest.wrappers.PasswordRequestWrapper;

import java.util.List;
import java.util.Optional;

@Service
public class PasswordService {

    private final PasswordRepository passwordRepository;
    private UserSession userSession = UserSession.getSession();

    public PasswordService(PasswordRepository passwordRepository) {
        this.passwordRepository = passwordRepository;
    }

    public ResponseEntity<?> createPassword(PasswordRequestWrapper passwordWrapper) {
        try {
            if (userSession.getAccess() != Access.MODIFY) {
                return new ResponseEntity<>("No access to modify password", HttpStatus.FORBIDDEN);
            }

            Password password = new Password(
                    passwordWrapper.getWebAddress(),
                    passwordWrapper.getDescription(),
                    passwordWrapper.getLogin(),
                    userSession.getUser()
            );
            password.setPassword(encryptPassword(passwordWrapper.getPassword(), userSession.getUser().getPasswordHash()));
            passwordRepository.save(password);
            return new ResponseEntity<>(password, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<?> getSessionUserPasswords() {
        Optional<List<Password>> passwords = passwordRepository.findByUserId(userSession.getUser().getId());
        if (passwords.isPresent()) {
            return new ResponseEntity<>(passwords.get(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("We couldn't find any passwords for current user.", HttpStatus.NOT_FOUND);
        }
    }

    public ResponseEntity<?> getDecryptedPassword(Integer id) {
        try {
            Optional<Password> optPassword = passwordRepository.findById(id);
            if (optPassword.isPresent()) {
                if (doesPasswordBelongToSessionUser(optPassword.get())) {
                    PasswordRequestWrapper passwordWrapper = new PasswordRequestWrapper(
                            optPassword.get().getLogin(),
                            optPassword.get().getWebAddress(),
                            optPassword.get().getDescription()
                    );
                    passwordWrapper.setPassword(decryptPassword(
                            optPassword.get().getPassword(),
                            userSession.getUser().getPasswordHash()
                    ));
                    return new ResponseEntity<>(passwordWrapper, HttpStatus.OK);
                }
            }
            return new ResponseEntity<>(
                    "We couldn't find password with given id or password doesn't belong to user.",
                    HttpStatus.NOT_FOUND
            );

        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<?> editPassword(PasswordRequestWrapper passwordWrapper) {
        try {
            if (userSession.getAccess() != Access.MODIFY) {
                return new ResponseEntity<>("No access to modify password", HttpStatus.FORBIDDEN);
            }

            Password password = new Password(passwordRepository.findById(passwordWrapper.getId()).get());
            password.setDescription(passwordWrapper.getDescription());
            password.setLogin(passwordWrapper.getLogin());
            password.setWebAddress(passwordWrapper.getWebAddress());
            password.setPassword(encryptPassword(passwordWrapper.getPassword(), userSession.getUser().getPasswordHash()));
            passwordRepository.save(password);
            return new ResponseEntity<>(password, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updatePasswordEncoding(String oldPasswd, String newPasswd) throws Exception {
        Optional<List<Password>> passwords = passwordRepository.findByUserId(userSession.getUser().getId());
        if(passwords.isPresent()) {
            List<Password> passwordList = passwords.get();
            for (Password passwd : passwordList) {
                String tempPasswd = decryptPassword(passwd.getPassword(), oldPasswd);
                passwd.setPassword(encryptPassword(tempPasswd, newPasswd));
            }
            passwordRepository.saveAll(passwordList);
        }
    }

    private String encryptPassword(String password, String userKey) throws Exception {
        return SecurityUtils.encrypt(password, SecurityUtils.generateKey(SecurityUtils.calculateMD5(userKey)));
    }

    private String decryptPassword(String password, String userKey) throws Exception {
        return SecurityUtils.decrypt(password, SecurityUtils.generateKey(SecurityUtils.calculateMD5(userKey)));
    }

    private Boolean doesPasswordBelongToSessionUser(Password password) {
        if (password.getUser().getId() == userSession.getUser().getId()) {
            return true;
        }
        return false;
    }
}
