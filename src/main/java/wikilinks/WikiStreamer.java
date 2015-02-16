package wikilinks;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 */
public class WikiStreamer {

    public static InputStream fromBunzipStream(InputStream bunzipStream) throws IOException {
        BZip2CompressorInputStream stream = new BZip2CompressorInputStream(bunzipStream, true);
        return stream;
    }



}
