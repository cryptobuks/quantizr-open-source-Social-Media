package quanta.service.ipfs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeType;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.LoadNodeFromIpfsRequest;
import quanta.request.PublishNodeToIpfsRequest;
import quanta.response.LoadNodeFromIpfsResponse;
import quanta.response.PublishNodeToIpfsResponse;
import quanta.service.mfs.SyncFromMFSService;
import quanta.service.mfs.SyncToMFSService;
import quanta.service.exports.ExportIpfsFile;
import quanta.util.Cast;
import quanta.util.Const;
import quanta.util.DateUtil;
import quanta.util.LimitedInputStreamEx;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.Val;

// IPFS Reference: https://docs.ipfs.io/reference/http/api

@Component
@Slf4j 
public class IPFSService extends ServiceBase {
    public static String API_ID;

    public final ConcurrentHashMap<String, Boolean> failedCIDs = new ConcurrentHashMap<>();

    public LinkedHashMap<String, Object> instanceId = null;
    Object instanceIdLock = new Object();

    /*
     * originally this was 'data-endcoding' (or at least i got that from somewhere), but now their
     * example page seems to show 'encoding' is the name here.
     */
    public String ENCODING_PARAM_NAME = "encoding";

    /*
     * RestTemplate is thread-safe and reusable, and has no state, so we need only one final static
     * instance ever
     */
    public final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
    public final RestTemplate restTemplateNoTimeout = new RestTemplate(Util.getClientHttpRequestFactory(0));
    public final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        API_ID = prop.getIPFSApiBase() + "/id";
    }

    /* On regular interval forget which CIDs have failed and allow them to be retried */
    @Scheduled(fixedDelay = 10 * DateUtil.MINUTE_MILLIS)
    public void clearFailedCIDs() {
        if (!MongoRepository.fullInit)
            return;
        failedCIDs.clear();
    }

    public LinkedHashMap<String, Object> getInstanceId() {
        if (!prop.ipfsEnabled())
            return null;
        synchronized (instanceIdLock) {
            if (instanceId == null) {
                instanceId = Cast.toLinkedHashMap(postForJsonReply(API_ID, LinkedHashMap.class));
            }
            return instanceId;
        }
    }

    /* Ensures this node's attachment is saved to IPFS and returns the CID of it */
    public String saveNodeAttachmentToIpfs(MongoSession ms, SubNode node) {
        checkIpfs();
        // todo-2: this is not yet handling multiple images. It's ok IPFS is on the back burner right now.
        Attachment att = node.getFirstAttachment();
        String cid = null;
        String mime = att != null ? att.getMime() : null;
        String fileName = att != null ? att.getFileName() : null;

        InputStream is = attach.getStreamByNode(node, "");
        if (is != null) {
            try {
                MerkleLink ret = addFromStream(ms, is, fileName, mime, null, false);
                if (ret != null) {
                    cid = ret.getHash();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                StreamUtil.close(is);
            }
        } else {
            log.debug("Unable to get inputstream or oid.");
        }

        return cid;
    }

    public MerkleLink addFileFromString(MongoSession ms, String text, String fileName, String mimeType, boolean wrapInFolder) {
        checkIpfs();
        InputStream stream = IOUtils.toInputStream(text);
        try {
            return addFromStream(ms, stream, fileName, mimeType, null, wrapInFolder);
        } finally {
            StreamUtil.close(stream);
        }
    }

    /*
     * NOTE: Default behavior according to IPFS docs is that without the 'pin' argument on this call it
     * DOES pin the file
     */
    public MerkleLink addFromStream(MongoSession ms, InputStream stream, String fileName, String mimeType,
            Val<Integer> streamSize, boolean wrapInFolder) {
        checkIpfs();
        String endpoint = prop.getIPFSApiBase() + "/add?stream-channels=true";
        if (wrapInFolder) {
            endpoint += "&wrap-with-directory=true";
        }
        return writeFromStream(ms, endpoint, stream, fileName, streamSize);
    }

    // public Map<String, Object> addTarFromFile(String fileName) {
    // arun.run(as -> {
    // try {
    // addTarFromStream(as, new BufferedInputStream(new FileInputStream(fileName)), null, null);
    // } catch (Exception e) {
    // log.error("Failed in restTemplate.exchange", e);
    // }
    // return null;
    // });
    // return null;
    // }

    // public MerkleLink addTarFromStream(MongoSession ms, InputStream stream, Val<Integer> streamSize,
    // Val<String> cid) {
    // return writeFromStream(ms, API_TAR + "/add", stream, null, streamSize, cid);
    // }

    // https://medium.com/red6-es/uploading-a-file-with-a-filename-with-spring-resttemplate-8ec5e7dc52ca
    /*
     * todo-2: addition of 'fileName' is very new and very important here. Evaluate everywhere we can
     * pass this in and also check if there are ways we can avoid the old need for mime guessing by
     * always basing off extension on this filename?
     */
    public MerkleLink writeFromStream(MongoSession ms, String endpoint, InputStream stream, String fileName,
            Val<Integer> streamSize) {
        checkIpfs();
        // log.debug("Write stream to endpoint: " + endpoint);
        MerkleLink ret = null;
        try {
            HttpHeaders headers = new HttpHeaders();

            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            LimitedInputStreamEx lis = new LimitedInputStreamEx(stream, user.getMaxUploadSize(ms));
            bodyMap.add("file", makeFileEntity(lis, fileName));

            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCodeValue() != 200) {
                throw new RuntimeException("Failed. StatusCode: " + response.getStatusCode());
            }

            // MediaType contentType = response.getHeaders().getContentType();

            log.debug("writeFromStream Raw Response: " + XString.prettyPrint(response));

            if (StringUtils.isEmpty(response.getBody())) {
                log.debug("no response body");
            } else {
                String body = response.getBody();
                try {
                    ret = XString.jsonMapper.readValue(body, MerkleLink.class);
                } catch (Exception e) {
                    // some calls, like the mfs file add, don't send back the MerkleLink, so for now let's just tolerate
                    // that
                    // until we design better around it, and return a null.
                    // log.debug("Unable to parse response string: " + body);
                }

                // log.debug("writeFromStream Response JSON: " + XString.prettyPrint(ret));
            }

            if (streamSize != null) {
                streamSize.setVal((int) lis.getCount());
            }
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
            throw e;
        }
        return ret;
    }

    // Creates a single file entry for a multipart file upload HTTP post
    public HttpEntity<InputStreamResource> makeFileEntity(InputStream is, String fileName) {
        checkIpfs();
        if (StringUtils.isEmpty(fileName)) {
            fileName = "file";
        }
        MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
        ContentDisposition contentDisposition = ContentDisposition.builder("form-data").name("file").filename(fileName).build();
        fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        HttpEntity<InputStreamResource> fileEntity = new HttpEntity<>(new InputStreamResource(is), fileMap);
        return fileEntity;
    }

    /*
     * Creates a node holding this CID in the current user (SessionContext) account under their EXPORTS
     * node type.
     * 
     * todo-2: need to document this (and how user must delete the export node to release their pins) in
     * the User Guide
     *
     * Note: childerenFiles will be all the files linked into this resource under a common DAG, and we
     * have to add them here, primarily to ensure garbage collector will keep them, but secondly it's a
     * nice-feature for user to be able to browse them individually.
     */
    public void writeIpfsExportNode(MongoSession ms, String cid, String mime, String fileName,
            List<ExportIpfsFile> childrenFiles) {
        checkIpfs();
        SubNode exportParent =
                read.getUserNodeByType(ms, ms.getUserName(), null, "### Exports", NodeType.EXPORTS.s(), null, null);

        if (exportParent != null) {
            SubNode node =
                    create.createNode(ms, exportParent, null, NodeType.NONE.s(), 0L, CreateNodeLocation.FIRST, null, null, true, true);

            // todo-2: make this handle multiple attachments, and all calls to it
            Attachment att = node.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), true, false);

            node.setOwner(exportParent.getOwner());
            // use export filename here
            node.setContent("IPFS Export: " + cid + "\n\nMime: " + mime);
            node.touch();

            att.setIpfsLink(cid);
            att.setMime(mime);
            att.setFileName(fileName);

            update.save(ms, node);

            if (childrenFiles != null) {
                for (ExportIpfsFile file : childrenFiles) {
                    SubNode child =
                            create.createNode(ms, node, null, NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, null, null, true, true);

                    // todo-2: make this handle multiple attachments, and all calls to it
                    Attachment childAtt = child.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), true, false);
                    child.setOwner(exportParent.getOwner());
                    child.setContent("IPFS File: " + file.getFileName() + "\n\nMime: " + file.getMime());
                    child.touch();

                    childAtt.setIpfsLink(file.getCid());
                    childAtt.setMime(file.getMime());
                    childAtt.setFileName(file.getFileName());
                    childAtt.setCssSize("200px");

                    update.save(ms, child);
                }
            }
        }
    }

    public void streamResponse(HttpServletResponse response, MongoSession ms, String hash, String mimeType) {
        checkIpfs();
        BufferedInputStream inStream = null;
        BufferedOutputStream outStream = null;

        try {
            /*
             * To set contentType and contentLength here we'd need to read the entire stream into byte array and
             * get that info, and then use the byte array to stream the result. For now things seem to work
             * without us holding it all in memory which is ideal
             */
            // response.setContentType(mimeTypeProp);
            // response.setContentLength((int) size);
            response.setHeader("Cache-Control", "public, max-age=31536000");

            inStream = new BufferedInputStream(getStream(ms, hash));
            outStream = new BufferedOutputStream(response.getOutputStream());

            IOUtils.copy(inStream, outStream);
            outStream.flush();

        } catch (final Exception e) {
            log.error(e.getMessage());
        } finally {
            StreamUtil.close(inStream, outStream);
        }
    }

    public InputStream getStream(MongoSession ms, String hash) {
        checkIpfs();
        if (failedCIDs.get(hash) != null) {
            // log.debug("Abort CID already failed: " + hash);
            throw new RuntimeException("failed CIDs: " + hash);
        }

        String sourceUrl = prop.getIPFSGatewayHostAndPort() + "/ipfs/" + hash;

        try {
            int timeout = 15;
            RequestConfig config = RequestConfig.custom() //
                    .setConnectTimeout(timeout * 1000) //
                    .setConnectionRequestTimeout(timeout * 1000) //
                    .setSocketTimeout(timeout * 1000).build();

            HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
            HttpGet request = new HttpGet(sourceUrl);

            request.addHeader("User-Agent", Const.FAKE_USER_AGENT);
            HttpResponse response = client.execute(request);
            InputStream is = response.getEntity().getContent();
            return is;
        } catch (Exception e) {
            failedCIDs.put(hash, true);
            log.error("getStream failed: " + sourceUrl, e);
            throw new RuntimeEx("Streaming failed.", e);
        }
    }

    public PublishNodeToIpfsResponse publishNodeToIpfs(MongoSession ms, PublishNodeToIpfsRequest req) {
        checkIpfs();
        ThreadLocals.requireAdmin();
        PublishNodeToIpfsResponse res = new PublishNodeToIpfsResponse();
        SyncToMFSService svc = (SyncToMFSService) context.getBean(SyncToMFSService.class);
        svc.writeIpfsFiles(ms, req, res);
        return res;
    }

    public LoadNodeFromIpfsResponse loadNodeFromIpfs(MongoSession ms, LoadNodeFromIpfsRequest req) {
        checkIpfs();
        ThreadLocals.requireAdmin();
        LoadNodeFromIpfsResponse res = new LoadNodeFromIpfsResponse();
        SyncFromMFSService svc = (SyncFromMFSService) context.getBean(SyncFromMFSService.class);
        svc.writeNodes(ms, req, res);
        return res;
    }

    public Object postForJsonReply(String url, Class<?> clazz) {
        checkIpfs();
        Object ret = null;
        try {
            // log.debug("post: " + url);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(null, null);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            // MediaType contentType = response.getHeaders().getContentType();
            // Warning: IPFS is inconsistent. Sometimes they return plain/text and sometimes
            // JSON in the contentType, so we just ignore it
            if (response.getStatusCode().value() == 200 /* && MediaType.APPLICATION_JSON.equals(contentType) */) {
                String body = response.getBody();
                if (clazz == String.class) {
                    return response.getBody() == null ? "success" : body;
                } else {
                    // log.debug("postForJsonReply: " + body);
                    if (body == null) {
                        ret = "success";
                    } else {
                        try {
                            ret = XString.jsonMapper.readValue(response.getBody(), clazz);
                        } catch (Exception e) {
                            log.error("Failed to parse body: " + body + " to class " + clazz.getName(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }
}
