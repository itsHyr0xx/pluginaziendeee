# PluginAziende

Plugin Paper per Minecraft 1.21.8 (Java 21) con aziende, ranghi collegati a LuckPerms e inviti di assunzione.

## Requisiti

- Paper 1.21.8
- Java 21
- LuckPerms installato

## Compilazione

Da una macchina con Maven e accesso a Internet:

```bash
mvn clean package
```

Il plugin compilato si trova in `target/PluginAziende.jar`. Copialo nella cartella `plugins` insieme a LuckPerms e riavvia il server.

## Comandi

- `/staffaziende crea <azienda> <gruppo-massimo>` — crea un'azienda. Il gruppo massimo deve già esistere in LuckPerms.
- `/staffaziende crea rango <azienda> <nome-rango> <gruppo-luckperms>` — aggiunge un rango aziendale collegato a un gruppo LuckPerms esistente.
- `/azienda capo <azienda>` — il primo giocatore che esegue il comando reclama l'azienda e riceve il gruppo massimo. Ogni azienda può avere un solo capo.
- `/azienda assumi <giocatore> <rango>` — il capo invita un giocatore online entro 10 blocchi. Il destinatario accetta o rifiuta con i due pulsanti grigi nell'inventario; l'invito scade dopo un minuto. Anche al momento dell'accettazione i giocatori devono essere entro 10 blocchi.

`/staffaziende` richiede il permesso `staffaziende` (predefinito: operatori). Per nomi azienda e rango con più parole, usa `_`, ad esempio `Sotto_tenente`.

I dati persistenti vengono salvati in `plugins/PluginAziende/aziende.yml`.
