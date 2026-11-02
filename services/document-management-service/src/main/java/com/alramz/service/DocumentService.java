package com.alramz.service;

import com.alramz.model.DmsFilingCategoriesResponse;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSaveDocumentResponse;
import com.alramz.model.DmsSearchRequest;
import com.alramz.model.DmsSearchResponse;
import org.springframework.web.multipart.MultipartFile;

/** DMS orchestration over Open Bee. Every method takes the resolved consumer; Open Bee uses its own key. */
public interface DocumentService {

    DmsFilingCategoriesResponse listFilingCategories(ConsumerContext consumer);

    DmsSaveDocumentResponse save(ConsumerContext consumer, MultipartFile file, DmsSaveDocumentRequest request);

    DmsSearchResponse search(ConsumerContext consumer, DmsSearchRequest request);

    /** Headers plus an InputStream-backed body; the caller must close the stream. */
    DmsDocumentContent download(ConsumerContext consumer, long documentId);
}
