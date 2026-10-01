package com.mandao.evidence;

/** Almacenamiento de objetos privado. Los archivos nunca se exponen directamente: se sirven vía API con permisos. */
public interface StorageService {

    /** Guarda el contenido y devuelve la llave del objeto. */
    String put(byte[] data, String contentType, String prefix, String extension);

    byte[] get(String key);

    void delete(String key);
}
