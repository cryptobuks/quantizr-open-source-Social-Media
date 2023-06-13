package quanta.service;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import quanta.config.ServiceBase;
import quanta.exception.OutOfSpaceException;
import quanta.exception.base.RuntimeEx;
import quanta.instrument.PerfMon;
import quanta.model.UserStats;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrivilegeType;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.DeleteAttachmentRequest;
import quanta.request.UploadFromIPFSRequest;
import quanta.request.UploadFromUrlRequest;
import quanta.response.DeleteAttachmentResponse;
import quanta.response.UploadFromIPFSResponse;
import quanta.response.UploadFromUrlResponse;
import quanta.response.UploadResponse;
import quanta.service.imports.ImportZipService;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.ImageUtil;
import quanta.util.LimitedInputStream;
import quanta.util.LimitedInputStreamEx;
import quanta.util.MimeTypeUtils;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Service for managing node attachments.
 *
 * Node attachments are binary attachments that the user can opload onto a node. Each node allows
 * either zero or one attachments. Uploading a new attachment wipes out and replaces the previous
 * attachment. If the attachment is an 'image' type then it gets displayed right on the page.
 * Otherwise a download link is what gets displayed on the node.
 */
@Component
public class AttachmentService extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(AttachmentService.class);

    @Autowired
    public GridFsTemplate grid;

    @Autowired
    public GridFSBucket gridBucket;

    public UploadResponse parseUploadFiles(MongoSession ms, MultipartFile[] uploadFiles) {
        UploadResponse resp = new UploadResponse();
        List<String> payloads = new LinkedList<String>();
        resp.setPayloads(payloads);
        long maxFileSize = user.getUserStorageRemaining(ms);

        for (MultipartFile uploadFile : uploadFiles) {
            String contentType = uploadFile.getContentType();
            // Right now we only support parsing EML files.
            if (!"message/rfc822".equals(contentType)) {
                continue;
            }
            try {
                LimitedInputStreamEx limitedIs = new LimitedInputStreamEx(uploadFile.getInputStream(), maxFileSize);
                String mkdown = mail.convertEmailToMarkdown(limitedIs);
                resp.setSuccess(true);
                payloads.add(mkdown);
            } catch ( //
                Exception e
            ) {
                throw ExUtil.wrapEx(e);
            }
        }
        return resp;
    }

    /*
     * Upload from User's computer. Standard HTML form-based uploading of a file from user machine
     */
    public ResponseEntity<?> uploadMultipleFiles(
        MongoSession ms,
        String attName,
        String nodeId,
        MultipartFile[] uploadFiles,
        boolean explodeZips,
        boolean toIpfs
    ) {
        if (toIpfs) {
            checkIpfs();
        }
        if (nodeId == null) {
            throw ExUtil.wrapEx("target nodeId not provided");
        }
        try {
            /*
             * NEW LOGIC: If the node itself currently has an attachment, leave it alone and just upload
             * UNDERNEATH this current node. Pass allowAuth=false here because below we check the ownerAuth
             * which will be even more strict.
             */
            SubNode node = null;
            boolean allowEmailParse = false;
            // if auto then get the node and make nodeId be that id.
            if (nodeId.equals("[auto]")) {
                allowEmailParse = true;
                SubNode parent = read.getNode(ms, "~" + NodeType.NOTES, false, null);
                if (parent == null) {
                    throw ExUtil.wrapEx("Node not found.");
                }
                node =
                    create.createNode(
                        ms,
                        parent,
                        null,
                        NodeType.NONE.s(), //
                        0L,
                        CreateNodeLocation.FIRST,
                        null,
                        null,
                        true,
                        true
                    );
                nodeId = node.getIdStr();
            } else {
                node = read.getNode(ms, nodeId, false, null);
            }
            if (node == null) {
                throw ExUtil.wrapEx("Node not found.");
            }
            auth.ownerAuth(node);
            long maxFileSize = user.getUserStorageRemaining(ms);
            int imageCount = 0;
            /*
             * if uploading multiple files check quota first, to make sure there's space for all files before we
             * start uploading any of them If there's only one file, the normal flow will catch an out of space
             * problem, so we don't need to do it in advance in here as we do for multiple file uploads only.
             *
             * Also we only do this check if not admin. Admin can upload unlimited amounts.
             */
            if (!ms.isAdmin() && uploadFiles.length > 1) {
                SubNode userNode = read.getUserNodeByUserName(null, null);
                // get how many bytes of storage the user currently holds
                Long binTotal = userNode.getInt(NodeProp.BIN_TOTAL);
                if (binTotal == null) {
                    binTotal = 0L;
                }
                // get max amount user is allowed
                Long userQuota = userNode.getInt(NodeProp.BIN_QUOTA);
                for (MultipartFile uploadFile : uploadFiles) {
                    binTotal += uploadFile.getSize();
                    // check if user went over max and fail the API call if so.
                    if (binTotal > userQuota) {
                        throw new OutOfSpaceException();
                    }
                }
            }
            for (MultipartFile uploadFile : uploadFiles) {
                String fileName = uploadFile.getOriginalFilename();
                String contentType = uploadFile.getContentType();
                if (contentType.startsWith("image/")) {
                    imageCount++;
                }
                long size = uploadFile.getSize();
                if (!StringUtils.isEmpty(fileName)) {
                    log.debug("Uploading file: " + fileName + " contentType=" + contentType);
                    LimitedInputStreamEx limitedIs = new LimitedInputStreamEx(uploadFile.getInputStream(), maxFileSize);
                    // attaches AND closes the stream.
                    attachBinaryFromStream(
                        ms,
                        false,
                        attName,
                        node,
                        nodeId,
                        fileName,
                        size,
                        limitedIs,
                        contentType,
                        -1,
                        -1,
                        explodeZips,
                        toIpfs,
                        true,
                        true,
                        true,
                        null,
                        allowEmailParse
                    );
                }
            }
            // if we have enough images to lay it out into a square of 3 cols switch to that
            // layout
            if (imageCount >= 9) {
                node.set(NodeProp.LAYOUT, "c3");
            } else // switch to that layout. // otherwise, if we have enough images to lay it out into a square of 2 cols
            if (imageCount >= 2) {
                node.set(NodeProp.LAYOUT, "c2");
            }
            update.saveSession(ms);
        } catch (Exception e) {
            throw ExUtil.wrapEx(e);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /*
     * Gets the binary attachment from a supplied stream and loads it into the repository on the node
     * specified in 'nodeId'
     */
    public void attachBinaryFromStream(
        MongoSession ms,
        boolean importMode,
        String attName,
        SubNode node,
        String nodeId,
        String fileName,
        long size,
        LimitedInputStreamEx is,
        String mimeType,
        int width,
        int height,
        boolean explodeZips,
        boolean toIpfs,
        boolean calcImageSize,
        boolean closeStream,
        boolean storeLocally,
        String sourceUrl,
        boolean allowEmailParse
    ) {
        /*
         * If caller already has 'node' it can pass node, and avoid looking up node again
         */
        if (node == null && nodeId != null) {
            node = read.getNode(ms, nodeId);
        }
        auth.ownerAuth(ms, node);
        /* mimeType can be passed as null if it's not yet determined */
        if (mimeType == null) {
            mimeType = getMimeFromFileType(fileName);
        }
        if (allowEmailParse && "message/rfc822".equals(mimeType)) {
            // this is EML file format.
            String mkdown = mail.convertEmailToMarkdown(is);
            node.setContent(mkdown);
            update.save(ms, node);
        } else if (explodeZips && "application/zip".equalsIgnoreCase(mimeType)) { //
            /*
             * This is a prototype-scope bean, with state for processing one import at a time
             */
            ImportZipService importZipStreamService = (ImportZipService) context.getBean(ImportZipService.class);
            importZipStreamService.importFromStream(ms, is, node, false);
        } else { //
            saveBinaryStreamToNode(
                ms,
                importMode,
                attName,
                is,
                mimeType,
                fileName,
                size,
                width,
                height,
                node,
                toIpfs,
                calcImageSize,
                closeStream,
                storeLocally,
                sourceUrl
            );
        }
    }

    public void pinLocalIpfsAttachments(SubNode node) {
        if (node == null || node.getAttachments() == null) return;
        node
            .getAttachments()
            .forEach((String key, Attachment att) -> {
                /*
                 * If we have an IPFS attachment and there's no IPFS_REF property that means it should be pinned.
                 * (IPFS_REF means 'referenced' and external to our server).
                 */
                if (att.getIpfsLink() != null) {
                    // if there's no 'ref' property this is not a foreign reference, which means we
                    // DO pin this.
                    if (att.getIpfsRef() == null) {
                        arun.run(sess -> {
                            // don't pass the actual node into here, because it runs in a separate thread and would be
                            // a concurrency problem.
                            ipfsPin.ipfsAsyncPinNode(sess, node.getId());
                            return null;
                        });
                    } else { // otherwise we don't pin it.
                        /*
                         * Don't do this removePin. Leave this comment here as a warning of what NOT to do! We can't simply
                         * remove the CID from our IPFS database because some node stopped using it, because there may be
                         * many other users/nodes potentially using it, so we let the releaseOrphanIPFSPins be our only way
                         * pins ever get removed, because that method does a safe and correct delete of all pins that are
                         * truly no longer in use by anyone
                         */
                        // ipfs.removePin(ipfsLink);
                    }
                }
            });
    }

    public void fixAllAttachmentMimes(SubNode node) {
        if (node == null || node.getAttachments() == null) return;
        node
            .getAttachments()
            .forEach((String key, Attachment att) -> {
                String mimeType = att.getMime();
                // ensure we have the best mimeType we can if not set in the data.
                if (StringUtils.isEmpty(mimeType)) {
                    String binUrl = att.getUrl();
                    if (!StringUtils.isEmpty(binUrl)) {
                        mimeType = getMimeTypeFromUrl(binUrl);
                        if (!StringUtils.isEmpty(mimeType)) {
                            att.setMime(mimeType);
                        }
                    }
                }
            });
    }

    public String getMimeTypeFromUrl(String url) {
        String mimeType = null;
        // try to get mime from name first.
        mimeType = URLConnection.guessContentTypeFromName(url);
        // if didn't get mime from name, try reading the actual url
        if (StringUtils.isEmpty(mimeType)) {
            int timeout = 60; // seconds
            try {
                URLConnection conn = new URL(url).openConnection();
                conn.setConnectTimeout(timeout * 1000);
                conn.setReadTimeout(timeout * 1000);
                mimeType = conn.getContentType();
            } catch (Exception e) {}
        }
        return mimeType;
    }

    // Another way is this (according to baeldung site)
    // Path path = new File("product.png").toPath();
    // String mimeType = Files.probeContentType(path);
    //
    public String getMimeFromFileType(String fileName) {
        String mimeType = null;
        /* mimeType can be passed as null if it's not yet determined */
        if (mimeType == null) {
            mimeType = URLConnection.guessContentTypeFromName(fileName);
        }
        if (mimeType == null) {
            String ext = FilenameUtils.getExtension(fileName);
            mimeType = MimeTypeUtils.getMimeType(ext);
        }
        /* fallback to at lest some acceptable mime type */
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    public void saveBinaryStreamToNode(
        MongoSession ms,
        boolean importMode,
        String attName,
        LimitedInputStreamEx inputStream,
        String mimeType,
        String fileName,
        long size,
        int width,
        int height,
        SubNode node,
        boolean toIpfs,
        boolean calcImageSize,
        boolean closeStream,
        boolean storeLocally,
        String sourceUrl
    ) {
        /*
         * NOTE: Setting this flag to false works just fine, and is more efficient, and will simply do
         * everything EXCEPT calculate the image size
         */
        BufferedImage bufImg = null;
        byte[] imageBytes = null;
        InputStream isTemp = null;
        long maxFileSize = user.getUserStorageRemaining(ms);
        Attachment att = null;
        if (importMode) {
            att = node.getAttachment(attName, false, false);
            fileName = att.getFileName();
        } else {
            // if no attName given we try to use "primary", but if primary exists, we find a different name
            if (StringUtils.isEmpty(attName) && node.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false) != null) {
                attName = getNextAttachmentKey(node);
            }
            int maxAttOrdinal = getMaxAttachmentOrdinal(node);
            att = node.getAttachment(attName, true, true);
            att.setOrdinal(maxAttOrdinal + 1);
        }
        if (ImageUtil.isImageMime(mimeType)) {
            // default image to be 100% size
            att.setCssSize("100%");
            if (calcImageSize) {
                LimitedInputStream is = null;
                try {
                    is = new LimitedInputStreamEx(inputStream, maxFileSize);
                    imageBytes = IOUtils.toByteArray(is);
                    isTemp = new ByteArrayInputStream(imageBytes);
                    bufImg = ImageIO.read(isTemp);
                    try {
                        att.setWidth(bufImg.getWidth());
                        att.setHeight(bufImg.getHeight());
                    } catch (Exception e) {
                        /*
                         * reading files from IPFS caused this exception, and I didn't investigate why yet, because I don't
                         * think it's a bug in my code, but something in IPFS.
                         */
                        log.error("Failed to get image length.", e);
                    }
                } catch (Exception e) {
                    throw ExUtil.wrapEx(e);
                } finally {
                    if (closeStream) {
                        StreamUtil.close(is, isTemp);
                    }
                }
            }
        }
        att.setMime(mimeType);
        SubNode userNode = read.getNode(ms, node.getOwner());
        if (imageBytes == null) {
            try {
                att.setSize(size);
                if (toIpfs) {
                    writeStreamToIpfs(ms, attName, node, inputStream, mimeType, userNode);
                } else {
                    if (storeLocally) {
                        if (fileName != null) {
                            att.setFileName(fileName);
                        }
                        writeStream(ms, importMode, attName, node, inputStream, fileName, mimeType, userNode);
                    } else {
                        att.setUrl(sourceUrl);
                    }
                }
            } finally {
                if (closeStream) {
                    StreamUtil.close(inputStream);
                }
            }
        } else {
            LimitedInputStreamEx is = null;
            try {
                att.setSize((long) imageBytes.length);
                if (storeLocally) {
                    if (fileName != null) {
                        att.setFileName(fileName);
                    }
                    is = new LimitedInputStreamEx(new ByteArrayInputStream(imageBytes), maxFileSize);
                    if (toIpfs) {
                        writeStreamToIpfs(ms, attName, node, is, mimeType, userNode);
                    } else {
                        writeStream(ms, importMode, attName, node, is, fileName, mimeType, userNode);
                    }
                } else {
                    att.setUrl(sourceUrl);
                }
            } finally {
                StreamUtil.close(is);
            }
        }
        update.save(ms, node);
    }

    public String getNextAttachmentKey(SubNode node) {
        int imgIdx = 1;
        while (node.getAttachment("img" + String.valueOf(imgIdx), false, false) != null) {
            imgIdx++;
        }
        return "img" + String.valueOf(imgIdx);
    }

    /* appends all the attachments from sourceNode onto targetNode, leaving targetNode as is */
    public void mergeAttachments(SubNode sourceNode, SubNode targetNode) {
        if (sourceNode == null || targetNode == null) return;
        List<Attachment> atts = sourceNode.getOrderedAttachments();
        if (atts != null) {
            for (Attachment att : atts) {
                String newKey = getNextAttachmentKey(targetNode);
                att.setKey(newKey);
                targetNode.addAttachment(att);
            }
        }
    }

    public int getMaxAttachmentOrdinal(SubNode node) {
        int max = -1;
        if (node.getAttachments() != null) {
            for (String key : node.getAttachments().keySet()) {
                Attachment att = node.getAttachments().get(key);
                if (att.getOrdinal() > max) {
                    max = att.getOrdinal();
                }
            }
        }
        return max;
    }

    /*
     * Removes the attachment from the node specified in the request.
     */
    public DeleteAttachmentResponse deleteAttachment(MongoSession ms, DeleteAttachmentRequest req) {
        DeleteAttachmentResponse res = new DeleteAttachmentResponse();
        String nodeId = req.getNodeId();
        SubNode node = read.getNode(ms, nodeId);
        auth.ownerAuth(node);
        final List<String> attKeys = XString.tokenize(req.getAttName(), ",", true);
        if (attKeys != null) {
            for (String attKey : attKeys) {
                deleteBinary(ms, attKey, node, null, false);
            }
        }
        res.setSuccess(true);
        return res;
    }

    /**
     * Returns data for an attachment (Could be an image request, or any type of request for binary data
     * from a node). This is the method that services all calls from the browser to get the data for the
     * attachment to download/display the attachment.
     *
     * the saga continues, after switching to InputStreamResouce images fail always with this error in
     * js console::
     *
     * InputStream has already been read - do not use InputStreamResource if a stream needs to be read
     * multiple times
     *
     * I stopped using this method (for now) because of this error, which is a Spring problem and not in
     * my code. I created the simpler getBinary() version (below) which works find AND is simpler.
     *
     * If 'download' is true we send back a "Content-Disposition: attachment;" rather than the default
     * of "inline" by omitting it
     *
     * node can be passed in -or- nodeId. If node is passed nodeId can be null.
     */
    @PerfMon(category = "attach")
    public void getBinary(
        MongoSession ms,
        String attName,
        SubNode node,
        String nodeId,
        String binId,
        boolean download,
        HttpServletResponse response
    ) {
        BufferedInputStream inStream = null;
        BufferedOutputStream outStream = null;
        try {
            ms = ThreadLocals.ensure(ms);
            if (node == null) {
                node = read.getNode(ms, nodeId, false, null);
            } else {
                nodeId = node.getIdStr();
            }
            if (node == null) {
                throw ExUtil.wrapEx("node not found.");
            }
            Attachment att = null;
            // todo-2: put this in a method (finding attachment by binId)
            if (node.getAttachments() != null) {
                for (String key : node.getAttachments().keySet()) {
                    Attachment curAtt = node.getAttachments().get(key);
                    if (curAtt.getBin() != null && curAtt.getBin().equals(binId)) {
                        att = curAtt;
                        attName = key;
                        break;
                    }
                }
            }
            if (att == null) {
                att = node.getAttachment(attName, false, false);
                if (att == null) {
                    throw ExUtil.wrapEx("attachment info not found.");
                }
            }
            boolean ipfs = StringUtils.isNotEmpty(att.getIpfsLink());
            // Everyone's account node can publish it's attachment and is assumed to be an
            // avatar.
            boolean allowAuth = true;
            if (auth.isAnAccountNode(ms, node)) {
                allowAuth = false;
            }
            if (allowAuth) {
                auth.auth(ms, node, PrivilegeType.READ);
            }
            String mimeTypeProp = att.getMime();
            if (mimeTypeProp == null) {
                throw ExUtil.wrapEx("unable to find mimeType property");
            }
            String fileName = att.getFileName();
            if (fileName == null) {
                fileName = "filename";
            }
            InputStream is = getStream(ms, attName, node, allowAuth);
            if (is == null) {
                throw new RuntimeException("Image not found.");
            }
            long size = att.getSize();
            response.setContentType(mimeTypeProp);
            /*
             * we gracefully tolerate the case where no size is available but normally it will be there.
             *
             * todo-2: when we detect this and then stream back some data should be just go ahead and SET the
             * correct 'size' on the node at that point?
             */
            if (size > 0) {
                /*
                 * todo-2: I'm getting the "disappearing image" (from the browser) network problem related to size
                 * (content length), but not calling 'contentLength()' below is a workaround.
                 *
                 * You get this error if you just wait about 30s to 1 minute, and maybe scroll out of view and back
                 * into view the images. What happens is the image loads just fine but then some background thread
                 * in Chrome looks at content lengths and finds some thing off somehoe and decides to make the image
                 * just disappear and show a broken link icon instead.
                 *
                 * SO... I keep having to come back and remove the setContentLength every time I think this problem
                 * is resolved and then later find out it isn't. Somehow this is *currently* only happening for
                 * images that are served up from IPFS storage.
                 *
                 * Chrome shows this: Failed to load resource: net::ERR_CONTENT_LENGTH_MISMATCH
                 */
                if (!ipfs) {
                    response.setContentLength((int) size);
                }
            }
            if (download) {
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            }
            // This is max allowed caching time, and is 1 year in seconds
            response.setHeader("Cache-Control", "public, max-age=31536000");
            inStream = new BufferedInputStream(is);
            outStream = new BufferedOutputStream(response.getOutputStream());
            IOUtils.copy(inStream, outStream);
            outStream.flush();
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            StreamUtil.close(inStream, outStream);
        }
    }

    /**
     * Downloads a file by name that is expected to be in the Admin Data Folder
     */
    public void getFile(MongoSession ms, String fileName, String disposition, HttpServletResponse response) {
        if (fileName.contains("..")) throw ExUtil.wrapEx("bad request.");
        BufferedInputStream inStream = null;
        BufferedOutputStream outStream = null;
        try {
            String fullFileName = prop.getAdminDataFolder() + File.separator + fileName;
            File file = new File(fullFileName);
            String checkPath = file.getCanonicalPath();
            if (!checkPath.startsWith(prop.getAdminDataFolder())) throw ExUtil.wrapEx("bad request.");
            if (!file.isFile()) throw ExUtil.wrapEx("file not found.");
            String mimeType = MimeTypeUtils.getMimeType(file);
            if (disposition == null) {
                disposition = "inline";
            }
            response.setContentType(mimeType);
            response.setContentLength((int) file.length());
            response.setHeader("Content-Disposition", disposition + "; filename=\"" + fileName + "\"");
            // This is max allowed caching time, and is 1 year in seconds
            response.setHeader("Cache-Control", "public, max-age=31536000");
            FileInputStream is = new FileInputStream(fullFileName);
            inStream = new BufferedInputStream(is);
            outStream = new BufferedOutputStream(response.getOutputStream());
            IOUtils.copy(inStream, outStream);
            outStream.flush();
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        } finally {
            StreamUtil.close(inStream, outStream);
        }
    }

    public ResponseEntity<StreamingResponseBody> getFileSystemResourceStream(MongoSession ms, String nodeId, String disposition) {
        if (!ms.isAdmin()) {
            throw new RuntimeEx("unauthorized");
        }
        try {
            SubNode node = read.getNode(ms, nodeId, false, null);
            if (node == null) {
                throw new RuntimeEx("node not found: " + nodeId);
            }
            String fullFileName = node.getStr(NodeProp.FS_LINK);
            File file = new File(fullFileName);
            if (!file.exists() || !file.isFile()) {
                throw new RuntimeEx("File not found: " + fullFileName);
            }
            String mimeType = MimeTypeUtils.getMimeType(file);
            if (disposition == null) {
                disposition = "inline";
            }
            /*
             * I think we could be using the MultipartFileSender here, eventually but not until we decople it
             * from reading directly from filesystem
             */
            AutoCloseInputStream acis = new AutoCloseInputStream(new FileInputStream(fullFileName));
            /*
             * I'm not sure if FileSystemResource is better than StreamingResponseBody, but i do know
             * StreamingResponseBody does EXACTLY what is needed which is to use a small buffer size and never
             * hold entire media file all in memory
             */
            StreamingResponseBody stream = os -> {
                IOUtils.copy(acis, os);
                os.flush();
            };
            return ResponseEntity
                .ok()
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.parseMediaType(mimeType))
                .body(stream);
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        }
    }

    public Object getStreamResource(MongoSession ms, HttpHeaders headers, String nodeId) {
        BufferedInputStream inStream = null;
        ResponseEntity<ResourceRegion> ret = null;
        try {
            SubNode node = read.getNode(ms, nodeId, false, null);
            Attachment att = node.getFirstAttachment();
            if (att == null) throw ExUtil.wrapEx("no attachment info found");
            auth.auth(ms, node, PrivilegeType.READ);
            String mimeTypeProp = att.getMime();
            if (mimeTypeProp == null) {
                throw ExUtil.wrapEx("unable to find mimeType property");
            }
            String fileName = att.getFileName();
            if (fileName == null) {
                fileName = "filename";
            }
            // long startTime = System.currentTimeMillis();
            InputStream is = getStream(ms, "", node, false);
            // if (session.isAdmin() && Const.adminDebugStreaming) {
            // long duration = System.currentTimeMillis() - startTime;
            // }
            // startTime = System.currentTimeMillis();
            long size = att.getSize();
            if (size == 0) {
                throw new RuntimeEx("Can\'t stream video without the file size. BIN_SIZE property missing");
            }
            inStream = new BufferedInputStream(is);
            byte[] bytes = IOUtils.toByteArray(inStream);
            ResourceRegion region = resourceRegion(new ByteArrayResource(bytes), headers);
            ret = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(MediaType.valueOf(mimeTypeProp)).body(region);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return ret;
    }

    private ResourceRegion resourceRegion(Resource resource, HttpHeaders headers) throws IOException {
        /*
         * todo-2: Will a smaller chunk size be better to get the video playing sooner after first clicked,
         * or will it do that at the cost of less overall resource effeciency? Need to research
         */
        long chunkSize = 500000L;
        long contentLength = resource.contentLength();
        HttpRange httpRange = headers.getRange().stream().findFirst().get();
        if (httpRange != null) {
            long start = httpRange.getRangeStart(contentLength);
            long end = httpRange.getRangeEnd(contentLength);
            long rangeLength = Long.min(chunkSize, end - start + 1);
            return new ResourceRegion(resource, start, rangeLength);
        } else {
            long rangeLength = Long.min(chunkSize, contentLength);
            return new ResourceRegion(resource, 0, rangeLength);
        }
    }

    /*
     * Uploads an attachment not from the user's machine but from some arbitrary internet URL they have
     * provided, that could be pointing to an image or any other kind of content actually.
     */
    public UploadFromUrlResponse readFromUrl(MongoSession ms, UploadFromUrlRequest req) {
        UploadFromUrlResponse res = new UploadFromUrlResponse();
        readFromUrl(ms, req.getSourceUrl(), null, req.getNodeId(), null, null, 0, req.isStoreLocally());
        res.setSuccess(true);
        return res;
    }

    public UploadFromIPFSResponse attachFromIPFS(MongoSession ms, UploadFromIPFSRequest req) {
        UploadFromIPFSResponse res = new UploadFromIPFSResponse();
        if (req.getNodeId() == null) {
            throw new RuntimeException("null nodeId");
        }
        SubNode node = read.getNode(ms, req.getNodeId());
        if (node == null) {
            throw new RuntimeException("node not found: id=" + req.getNodeId());
        }
        // todo-1: make this handle multiple attachments, and all calls to it
        Attachment att = node.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), true, true);
        auth.ownerAuth(node);
        att.setIpfsLink(req.getCid().trim());
        String mime = req.getMime().trim().replace(".", "");
        // If an extension was given (not a mime), then use it to make a filename, and
        // generate the mime from it.
        if (!mime.contains("/")) {
            att.setFileName("file." + mime);
            mime = MimeTypeUtils.getMimeType(mime);
        }
        att.setMime(mime);
        update.save(ms, node);
        res.setSuccess(true);
        return res;
    }

    /**
     * mimeHint This is an additional string invented because IPFS urls don't contain the file extension
     * always and in that case we need to get it from the IPFS filename itself and that's what the hint
     * is in that case. Normally however mimeHint is null
     *
     * 'inputStream' is a retrofit to this function for when we want to just call this method and get an
     * inputStream handed back that can be read from. Normally the inputStream Val is null and not used.
     *
     * NOTE: If 'node' is already available caller should pass it, or else can pass nodeId.
     */
    @PerfMon(category = "attach")
    public void readFromUrl(
        MongoSession ms,
        String sourceUrl,
        SubNode node,
        String nodeId,
        String mimeHint,
        String mimeType,
        long maxFileSize,
        boolean storeLocally
    ) {
        if (mimeType == null) {
            mimeType = getMimeTypeFromUrl(sourceUrl);
            if (StringUtils.isEmpty(mimeType) && mimeHint != null) {
                mimeType = URLConnection.guessContentTypeFromName(mimeHint);
            }
        }
        if (node == null) {
            node = read.getNode(ms, nodeId);
        }
        // only need to auth if we looked up the node.
        auth.ownerAuth(node);
        String attKey = getNextAttachmentKey(node);
        if (!storeLocally) {
            int maxAttOrdinal = getMaxAttachmentOrdinal(node);
            Attachment att = node.getAttachment(attKey, true, true);
            if (mimeType != null) {
                att.setMime(mimeType);
            }
            att.setUrl(sourceUrl);
            att.setOrdinal(maxAttOrdinal + 1);
            return;
        }
        if (maxFileSize <= 0) {
            maxFileSize = user.getUserStorageRemaining(ms);
        }
        ms = ThreadLocals.ensure(ms);
        LimitedInputStreamEx limitedIs = null;
        try {
            URL url = new URL(sourceUrl);
            int timeout = 20;
            RequestConfig config = RequestConfig
                .custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000)
                .build();
            /*
             * if this is an image extension, handle it in a special way, mainly to extract the width, height
             * from it
             */
            if (ImageUtil.isImageMime(mimeType)) {
                /*
                 * DO NOT DELETE
                 *
                 * Basic version without masquerading as a web browser can cause a 403 error because some sites
                 * don't want just any old stream reading from them. Leave this note here as a warning and
                 * explanation
                 */
                // would restTemplate be better for this ?
                HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                HttpGet request = new HttpGet(sourceUrl);
                request.addHeader("User-Agent", Const.FAKE_USER_AGENT);
                HttpResponse response = client.execute(request);
                log.debug(
                    "Response Code: " +
                    response.getStatusLine().getStatusCode() +
                    " reason=" +
                    response.getStatusLine().getReasonPhrase()
                );
                InputStream is = response.getEntity().getContent();
                limitedIs = new LimitedInputStreamEx(is, maxFileSize);
                // insert 0L for size now, because we don't know it yet
                attachBinaryFromStream(
                    ms,
                    false,
                    attKey,
                    node,
                    nodeId,
                    sourceUrl,
                    0L,
                    limitedIs,
                    mimeType,
                    -1,
                    -1,
                    false,
                    false,
                    true,
                    true,
                    storeLocally,
                    sourceUrl,
                    false
                );
            } else/*
             * if not an image extension, we can just stream directly into the database, but we want to try to
             * get the mime type first, from calling detectImage so that if we do detect its an image we can
             * handle it as one.
             */ {
                if (!detectAndSaveImage(ms, nodeId, attKey, sourceUrl, url, storeLocally)) {
                    HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                    HttpGet request = new HttpGet(sourceUrl);
                    request.addHeader("User-Agent", Const.FAKE_USER_AGENT);
                    HttpResponse response = client.execute(request);
                    /*
                     * log.debug("Response Code: " + response.getStatusLine().getStatusCode() + " reason=" +
                     * response.getStatusLine().getReasonPhrase());
                     */
                    InputStream is = response.getEntity().getContent();
                    limitedIs = new LimitedInputStreamEx(is, maxFileSize);
                    // insert 0L for size now, because we don't know it yet
                    attachBinaryFromStream(
                        ms,
                        false,
                        attKey,
                        node,
                        nodeId,
                        sourceUrl,
                        0L,
                        limitedIs,
                        "",
                        -1,
                        -1,
                        false,
                        false,
                        true,
                        true,
                        storeLocally,
                        sourceUrl,
                        false
                    );
                }
            }
        } catch (Exception e) {
            throw ExUtil.wrapEx(e);
        } finally {
            /* finally block just for extra safety */
            // this stream will have been closed by 'attachBinaryFromStream' but we close
            // here too anyway.
            StreamUtil.close(limitedIs);
        }
    }

    // FYI: Warning: this way of getting content type doesn't work.
    // String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
    //
    /* returns true if it was detected AND saved as an image */
    private boolean detectAndSaveImage(
        MongoSession ms,
        String nodeId,
        String attKey,
        String sourceUrl,
        URL url,
        boolean storeLocally
    ) {
        ImageInputStream is = null;
        LimitedInputStreamEx is2 = null;
        ImageReader reader = null;
        int maxFileSize = 20 * 1024 * 1024;
        try {
            is = ImageIO.createImageInputStream(url.openStream());
            Iterator<ImageReader> readers = ImageIO.getImageReaders(is);
            if (readers.hasNext()) {
                reader = readers.next();
                String formatName = reader.getFormatName();
                if (formatName != null) {
                    formatName = formatName.toLowerCase();
                    reader.setInput(is, true, false);
                    String mimeType = "image/" + formatName;
                    BufferedImage bufImg = reader.read(0);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    ImageIO.write(bufImg, formatName, os);
                    byte[] bytes = os.toByteArray();
                    is2 = new LimitedInputStreamEx(new ByteArrayInputStream(bytes), maxFileSize);
                    attachBinaryFromStream(
                        ms,
                        false,
                        attKey,
                        null,
                        nodeId,
                        sourceUrl,
                        bytes.length,
                        is2,
                        mimeType,
                        bufImg.getWidth(null),
                        bufImg.getHeight(null),
                        false,
                        false,
                        true,
                        true,
                        storeLocally,
                        sourceUrl,
                        false
                    );
                    return true;
                }
            }
        } catch (Exception e) {
            throw ExUtil.wrapEx(e);
        } finally {
            StreamUtil.close(is, is2, reader);
        }
        return false;
    }

    public void writeStream(
        MongoSession ms,
        boolean importMode,
        String attName,
        SubNode node,
        LimitedInputStreamEx stream,
        String fileName,
        String mimeType,
        SubNode userNode
    ) {
        // don't create attachment here, there shuold already be one, but we pass create=true anyway
        Attachment att = node.getAttachment(attName, !importMode, false);
        auth.ownerAuth(node);
        DBObject metaData = new BasicDBObject();
        metaData.put("nodeId", node.getId());
        if (userNode == null) {
            userNode = read.getUserNodeByUserName(null, null);
        }
        // if we're importing we should leave any binary alone
        if (!importMode) {
            /*
             * Delete any existing grid data stored under this node, before saving new attachment
             */
            deleteBinary(ms, attName, node, userNode, true);
        }
        String id = grid.store(stream, fileName, mimeType, metaData).toString();
        long streamCount = stream.getCount();
        // update the user quota which enforces their total storage limit
        if (!ms.isAdmin()) {
            user.addBytesToUserNodeBytes(ms, streamCount, userNode);
        }
        if (userNode == null) {
            throw new RuntimeEx("User not found.");
        }
        /*
         * Now save the node also since the property on it needs to point to GridFS id
         */
        att.setBin(id);
        att.setSize(streamCount);
        if (importMode) {
            log.debug("Setting attachment id to " + id + " on subNode " + att.getOwnerNode().getIdStr());
        }
    }

    public void writeStreamToIpfs(
        MongoSession ms,
        String attName,
        SubNode node,
        InputStream stream,
        String mimeType,
        SubNode userNode
    ) {
        auth.ownerAuth(node);
        Attachment att = node.getAttachment(attName, true, false);
        Val<Integer> streamSize = new Val<>();
        MerkleLink ret = ipfs.addFromStream(ms, stream, null, mimeType, streamSize, false);
        if (ret != null) {
            att.setIpfsLink(ret.getHash());
            att.setSize((long) streamSize.getVal());
            /* consume user quota space */
            user.addBytesToUserNodeBytes(ms, streamSize.getVal(), userNode);
        }
    }

    /*
     * Assumes owner 'ms' has already been auth-checked for owning this node. If 'gridOnly' is true that
     * means we should only delete from the GRID DB, and not touch any of the properties on the node
     * itself
     */
    public void deleteBinary(MongoSession ms, String attName, SubNode node, SubNode userNode, boolean gridOnly) {
        if (node == null) return;
        HashMap<String, Attachment> attachments = node.getAttachments();
        if (attachments == null) return;
        Attachment att = attachments.get(attName);
        if (att == null) return;
        if (!gridOnly) {
            attachments.remove(attName);
            node.setAttachments(attachments);
        }
        if (!ms.isAdmin()) {
            /*
             * NOTE: There is no equivalent to this on the IPFS code path for deleting ipfs becuase since we
             * don't do reference counting we let the garbage collecion cleanup be the only way user quotas are
             * deducted from
             */
            long totalBytes = user.getTotalAttachmentBytes(ms, node);
            user.addBytesToUserNodeBytes(ms, -totalBytes, userNode);
        }
        grid.delete(new Query(Criteria.where("_id").is(att.getBin())));
    }

    /*
     * Gets the binary data attachment stream from the node regardless of wether it's from IPFS_LINK or
     * BIN.
     *
     * tood-0: search all calls to this and verify attName is correct.
     */
    public InputStream getStream(MongoSession ms, String attName, SubNode node, boolean doAuth) {
        if (doAuth) {
            auth.auth(ms, node, PrivilegeType.READ);
        }
        Attachment att = node.getAttachment(attName, false, false);
        if (att == null) return null;
        InputStream is = null;
        String ipfsHash = att.getIpfsLink();
        if (ipfsHash != null) {
            /*
             * todo-2: When the IPFS link happens to be unreachable/invalid (or IFPS disabled?), this can
             * timeout here by taking too long. This wreaks havoc on the browser thread during some scenarios.
             * log.debug("Getting IPFS Stream for NodeId " + node.getIdStr() + " IPFS_CID=" + ipfsHash);
             */
            is = ipfs.getStream(ms, ipfsHash);
        } else {
            is = getStreamByNode(node, attName);
        }
        return is;
    }

    public InputStream getStreamByNode(SubNode node, String attName) {
        if (node == null) return null;
        // long startTime = System.currentTimeMillis();
        Attachment att = node.getAttachment(attName, false, false);
        if (att == null || att.getBin() == null) return null;
        /* why not an import here? */
        com.mongodb.client.gridfs.model.GridFSFile gridFile = grid.findOne(new Query(Criteria.where("_id").is(att.getBin())));
        // new Query(Criteria.where("metadata.nodeId").is(nodeId)));
        if (gridFile == null) {
            log.debug("gridfs ID not found");
            return null;
        }
        // long duration = System.currentTimeMillis() - startTime;
        // startTime = System.currentTimeMillis();
        GridFsResource gridFsResource = new GridFsResource(gridFile, gridBucket.openDownloadStream(gridFile.getObjectId()));
        // duration = System.currentTimeMillis() - startTime;
        // startTime = System.currentTimeMillis();
        try {
            InputStream is = gridFsResource.getInputStream();
            if (is == null) {
                throw new RuntimeEx("Unable to get inputStream");
            }
            // duration = System.currentTimeMillis() - startTime;
            return is;
        } catch (Exception e) {
            throw new RuntimeEx("unable to readStream", e);
        }
    }

    public String getStringByNode(MongoSession ms, SubNode node) {
        String ret = null;
        if (node != null) {
            auth.auth(ms, node, PrivilegeType.READ);
            ret = getStringByNodeEx(node);
        }
        return ret;
    }

    /* Gets the content of the grid resource by reading it into a string */
    public String getStringByNodeEx(SubNode node) {
        if (node == null) return null;
        log.debug("getStringByNode: " + node.getIdStr());
        Attachment att = node.getFirstAttachment();
        if (att == null || att.getBin() == null) return null;
        com.mongodb.client.gridfs.model.GridFSFile gridFile = grid.findOne(new Query(Criteria.where("_id").is(att.getBin())));
        // new Query(Criteria.where("metadata.nodeId").is(nodeId)));
        if (gridFile == null) {
            log.debug("gridfs ID not found");
            return null;
        }
        GridFsResource gridFsResource = new GridFsResource(gridFile, gridBucket.openDownloadStream(gridFile.getObjectId()));
        try {
            InputStream is = gridFsResource.getInputStream();
            if (is == null) {
                throw new RuntimeEx("Unable to get inputStream");
            }
            String result = IOUtils.toString(is, StandardCharsets.UTF_8.name());
            return result;
        } catch (Exception e) {
            throw new RuntimeEx("unable to readStream", e);
        }
    }

    public int getGridItemCount() {
        return arun.run(as -> {
            int count = 0;
            GridFSFindIterable files = gridBucket.find();
            /* Scan all files in the grid */
            if (files != null) {
                /*
                 * I am needing this quick and didn't find another way to do this other than brute force scan. Maybe
                 * they are using a linked list so that there genuinely is no faster way ?
                 */
                for (GridFSFile file : files) {
                    count++;
                }
            }
            return Integer.valueOf(count);
        });
    }

    /**
     * This method makes a single pass over all grid items doing all the daily maintenance on each one
     * as necessary to maintain the system health and statistics.
     *
     * Scans all the uploaded attachments, and finds any that aren't owned by some SubNode, and deletes
     * them.
     *
     * Also keeps totals by each user account, in a hashmap to be written all out at the end to all the
     * nodes.
     */
    public void gridMaintenanceScan(HashMap<ObjectId, UserStats> statsMap) {
        arun.run(as -> {
            int delCount = 0;
            // todo-1: do we need to replace this with a 'stream' of some kind to ensure we won't run out of
            // memory?
            GridFSFindIterable files = gridBucket.find();
            /* Scan all files in the grid */
            if (files != null) {
                for (GridFSFile file : files) {
                    Document meta = file.getMetadata();
                    if (meta != null) {
                        /* Get which nodeId owns this grid file */
                        ObjectId id = (ObjectId) meta.get("nodeId");
                        if (id != null) {
                            SubNode subNode = read.getNode(as, id);
                            /*
                             * If the node doesn't exist then this grid file is an orphan and should go away
                             */
                            if (subNode == null) {
                                log.debug("Grid Orphan Delete: " + id.toHexString());
                                // Query query = new Query(GridFsCriteria.where("_id").is(file.getId());
                                Query q = new Query(Criteria.where("_id").is(file.getId()));
                                // Note: It's not a bug that we don't call this here:
                                // usrMgr.addNodeBytesToUserNodeBytes(session, node, null, -1);
                                // Because all the userstats are updated at the end of this scan.
                                // todo-1: does grid support bulkOp deleting?
                                grid.delete(q);
                                delCount++;
                            } else/*
                             * else update the UserStats by adding the file length to the total for this user
                             */ {
                                UserStats stats = statsMap.get(subNode.getOwner());
                                if (stats == null) {
                                    stats = new UserStats();
                                    stats.binUsage = file.getLength();
                                    statsMap.put(subNode.getOwner(), stats);
                                } else {
                                    stats.binUsage = stats.binUsage.longValue() + file.getLength();
                                }
                            }
                        }
                    }
                }
            }
            Iterable<SubNode> accountNodes = read.getAccountNodes(as, null, null, null, -1, true, true);
            /*
             * scan all userAccountNodes, and set a zero amount for those not found (which will be the correct
             * amount).
             */
            for (SubNode accountNode : accountNodes) {
                log.debug("Processing Account Node: id=" + accountNode.getIdStr());
                UserStats stats = statsMap.get(accountNode.getOwner());
                if (stats == null) {
                    stats = new UserStats();
                    stats.binUsage = 0L;
                    statsMap.put(accountNode.getOwner(), stats);
                }
            }
            log.debug(String.valueOf(delCount) + " grid orphans deleted.");
            return null;
        });
    }
}
