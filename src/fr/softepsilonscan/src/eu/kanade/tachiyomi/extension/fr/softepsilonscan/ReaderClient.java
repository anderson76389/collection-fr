package eu.kanade.tachiyomi.extension.fr.softepsilonscan;

import eu.kanade.tachiyomi.multisrc.pam.Pam;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

// Java can delegate this member-extension override to Pam without changing the shared theme.
public abstract class ReaderClient extends Pam {
    protected abstract Interceptor renderedPageInterceptor();

    @Override
    protected OkHttpClient.Builder configureClient(OkHttpClient.Builder builder) {
        builder.addInterceptor(renderedPageInterceptor());
        return super.configureClient(builder);
    }
}
