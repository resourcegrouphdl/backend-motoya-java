package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Vehiculo {
    String id;
    String marca;
    String modelo;
    String anio;            // ⚠️ string en Firestore
    String color;
    Double precioReferencial;
    Double cilindrada;
    Timestamp createdAt;
    Timestamp updatedAt;

    public String getDescripcion() {
        return marca + " " + modelo + " " + anio + " - " + color;
    }
}
