package org.acme.getting.started.commandmode;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Start implements QuarkusApplication {

    @Override
    public int run(String... args) {
        try {
            DirectoryBookmarks.main(args);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return 0;
    }

}
