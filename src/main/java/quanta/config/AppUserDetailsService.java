package quanta.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import quanta.model.client.NodeProp;
import quanta.model.client.PrincipalName;
import quanta.mongo.model.SubNode;

/**
 * Part of Spring Security implementation
 */
@Service
@Slf4j 
public class AppUserDetailsService extends ServiceBase implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        if (PrincipalName.ADMIN.s().equals(userName)) {
            return new AppUserDetails(userName, prop.getAdminPassword());
        } else {
            SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, userName));
            if (userNode != null) {
                String pwdHash = userNode.getStr(NodeProp.PWD_HASH);
                return new AppUserDetails(userName, pwdHash);
            } else {
                throw new UsernameNotFoundException("Not found: " + userName);
            }
        }
    }
}
