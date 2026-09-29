package com.lawfirm.erp.modules.document.dto.request;

import lombok.Getter;
import lombok.Setter;

/** Confirms that the file has been POSTed to storage. The body may be omitted entirely. */
@Getter
@Setter
public class ConfirmUploadRequest {

    /** ETag storage returned for the upload. Optional — the real one is read back from storage. */
    private String etag;
}
