package com.motoyav2.auth.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

@Data
@Document(collectionName = "tienda_profiles")
public class StoreDocument {

    @DocumentId
    private String id;
    private String businessName;
    private String address;
    private String city;
}
