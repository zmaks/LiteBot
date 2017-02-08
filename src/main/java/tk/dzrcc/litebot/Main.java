package tk.dzrcc.litebot;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tk.dzrcc.litebot.telebot.LiteDozorBot;

/**
 * Created by Maksim on 27.01.2017.
 */
public class Main {

    public static void main(String[] args) {
        initBot();
    }

    private static  void  initBot() {
        ApiContextInitializer.init();

        TelegramBotsApi botsApi = new TelegramBotsApi();

        try {
            botsApi.registerBot(new LiteDozorBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
