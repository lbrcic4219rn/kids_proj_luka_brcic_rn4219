package app;

// Ova klasa služi samo da bi je nasledili ServantInfo i BootstrapServer. Zašto? Da bi novi node ušao u sistem, mora da pošalje prvo poruku
// bootstrap-u. Da bi poslao poruku bootstrap-u, mora da ima receiver info koji može biti ili Bootstrap ili ServantInfo. Treća opcija ne postoji.
// Dakle samo da bi mogli kao receiver-a u Message interface-u da stavimo ovaj tip.

import java.io.Serializable;

public interface Receiver extends Serializable {
    String getIpAddress();
    int getPort();
}
