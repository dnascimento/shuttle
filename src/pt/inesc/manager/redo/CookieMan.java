package pt.inesc.manager.redo;

import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CookieMan {
    // ID, values
    CookieIDtoList map = new CookieIDtoList();
    private static Logger logger = LogManager.getLogger("CookieMan");



    /**
     * Resolver este cookie para enviar para um novo pedido ao servidor
     * 
     * @param cookie original do pedido
     * @return cookie atual para o traduzir
     */
    public String toNewRequest(String originais) {
        String[] cookies = originais.split("; ");
        StringBuilder sb = new StringBuilder();
        String[] cookie;
        String newValue;
        // Fazer parse de todos os valores
        for (int i = 0; i < cookies.length; i++) {
            cookie = cookies[i].split("=");

            // Tentar traduzir
            CookieList listForId = map.get(cookie[0]);
            newValue = listForId.get(cookie[1]);

            sb.append(cookie[0]);
            sb.append("=");
            sb.append(newValue);
            sb.append("; ");
        }
        String finalCookie = sb.toString();
        finalCookie = finalCookie.substring(0, finalCookie.length() - 2);

        return finalCookie;
    }


    /**
     * A resposta nova pediu para fazer set a este valor, a original pediu para fazer ao
     * originalCookie
     * 
     * @param cookie
     * @param originalCookie
     */
    public void fromNewResponse(String newCookies, String originalCookie) {
        // Quero formar pares por ID e adicionar caso ainda nao existam

        // Convert Originals
        HashMap<String, String> originals = new HashMap<String, String>();
        String[] split;
        String[] cookies;
        String[] newCook;

        cookies = originalCookie.split("; ");
        for (int i = 0; i < cookies.length - 1; i++) {
            split = cookies[i].split("=");
            originals.put(split[0], split[1]);
        }

        // TODO Process path
        // Originals are parsed.
        // Parse news
        cookies = newCookies.split("; ");
        for (int i = 0; i < cookies.length - 1; i++) {
            newCook = cookies[i].split("=");

            // Tentar encontrar traducao no originals
            String originalCook = originals.remove(newCook[0]);

            // Formei um par
            map.get(newCook[0]).addCookie(originalCook, newCook[1]);
        }

        if (originals.size() > 0) {
            logger.error("Original Cookies remaining");
        }
    }
}
