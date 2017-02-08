package tk.dzrcc.litebot.game;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import tk.dzrcc.litebot.exception.LiteBotException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Maksim on 27.01.2017.
 */
public class Game {

    private WebClient webClient;

    private String gameUrl;

    private List<ArrayList<Code>> sectors;
    private List<GottenCode> gottenCodes;
    private String currentTaskNumber = "0";

    private Pattern sectorsPattern = Pattern.compile("основныекоды:[<spanstyle=\"color:red\">1-3<\\/span>,+]+<br");
    private Pattern codePattern = Pattern.compile("\\d\\+?");
    private Pattern taskNumPattern = Pattern.compile("Задание\\s\\d{1,2}");

    private static String CANNOT_INPUT_CODE = "Ошибка. Не получается вбить код :(";
    private static String CANNOT_LOAD_PAGE = "Что-то не получется загрузить страницу движка...";
    private static String CANNOT_FIND_TASK_NUMBER = "Что-то пошло не так. Не могу найти номер задания на странице движка.";
    private static String STRANGE_SITUATION = "Код принят, но почему-то не могу определить какой из. Зайдите на движок и чекните последние события игры.";

    public Game(String gameUrl) {
        this.gameUrl = gameUrl;
        init();
    }

    public String init(){
        webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        sectors = new ArrayList<ArrayList<Code>>();
        gottenCodes = new ArrayList<GottenCode>();
        try {
            firstGameLoad();
        } catch (LiteBotException e){
            return e.getMessage();
        }
        return "Авторизация прошла успешно.\n";

    }

    private void firstGameLoad() throws LiteBotException{
        HtmlPage gamePage;
        try {
            gamePage = getGamePage();
        } catch (IOException e) {
            e.printStackTrace();
            throw new LiteBotException(CANNOT_LOAD_PAGE);
        }
        String taskNum = parseTaskNumber(gamePage);
        System.out.println("Номер задания определен: "+taskNum);

        if (!taskNum.equals(currentTaskNumber)){
            System.out.print(". Это новое задание!");
            currentTaskNumber = taskNum;
            sectors = parseCodePage(gamePage);
            gottenCodes.clear();
        }

        if (sectors.isEmpty())
            sectors = parseCodePage(gamePage);
    }

    public synchronized CodeResponse performCode(String code, String player) {
        try {
            firstGameLoad();
        } catch (LiteBotException e) {
            return new CodeResponse(e.getMessage(), null);
        }
        HtmlPage codePage;
        try {
            codePage = inputCode(code);
        } catch (IOException | LiteBotException e) {
            e.printStackTrace();
            return new CodeResponse(e.getMessage(), null);
        }

        String sysMessage = parseSysMessage(codePage);
        System.out.println(sysMessage);
        Code inputCode = analyzeCodes(parseCodePage(codePage), code);

        if (inputCode == null) {
            GottenCode gottenCode = gottenCodes.stream().filter(x -> x.getCode().equals(code)).findFirst().orElse(null);
            return new CodeResponse(sysMessage, null, gottenCode);
        } else {
            gottenCodes.add(new GottenCode(code, player, new Date()));
            CodeResponse codeResponse = new CodeResponse(sysMessage, inputCode);
            codeResponse.setLevelStat(getLevelStatistic(sectors, inputCode.getSector(), inputCode.getLevel()));
            codeResponse.setSectorStat(getSectorStatistic(sectors, inputCode.getSector()));
            return codeResponse;
        }
    }

    private String getLevelStatistic(List<ArrayList<Code>> sectors, Integer sector, String level) {
        if (sectors.get(sector-1).size()==1) return null;
        List<Code> levelList = sectors.get(sector-1)
                .stream()
                .filter(x -> x.getLevel().equals(level))
                .collect(Collectors.toList());

        Long countFound = levelList
                .stream()
                .filter(x -> x.getGotten())
                .count();
        return countFound+"/"+levelList.size();
    }

    private String getSectorStatistic(List<ArrayList<Code>> sectors, Integer sector){
        Long countGottenInSector = 0L;
        countGottenInSector += sectors.get(sector-1)
                .stream()
                .filter(x -> x.getGotten())
                .count();
        return countGottenInSector+"/"+sectors.get(sector-1).size();
    }

    public String getTime(){
        HtmlPage page = null;
        try {
            page = getGamePage();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return parseTime(page);
    }

    public String getGameStatus(){
        firstGameLoad();
        StringBuilder response = new StringBuilder();
        response.append("Инфа по заданию №");
        response.append(currentTaskNumber);

        for (int i = 0; i < sectors.size(); i++) {
            if (sectors.size() > 1) {
                response.append("\n\n");
                response.append("Сектор ");
                response.append(i+1);
                response.append(":\n");
            } else
                response.append("\n\n");
            for (int j = 0; j < sectors.get(i).size(); j++) {
                ArrayList<Code> codes = sectors.get(i);
                response.append((j+1)+") ");
                Code curCode = codes.get(j);
                if (curCode.getGotten())
                    response.append(curCode.getCode());
                else
                    response.append("не взят");
                response.append(" (");
                response.append(curCode.getLevel());
                response.append(")\n");
            }
            List<String> list = sectors.get(i)
                    .stream()
                    .distinct()
                    .map(x -> x.getLevel())
                    .collect(Collectors.toList());
            if (list.size()>1) {
                response.append("По уровням сложности:\n");
                for (int j = 0; j < list.size(); j++) {
                    response.append("(");
                    response.append(list.get(j));
                    response.append(") - ");
                    response.append(getLevelStatistic(sectors,i+1, list.get(j)));
                    if (list.size()-1 != j) response.append(", ");
                }
            }
            response.append("\nВзято");
            if (sectors.size() > 1) response.append(" в секторе");
            response.append(":");
            response.append(getSectorStatistic(sectors,i+1));
        }
        return response.toString();
    }

    private String parseTaskNumber(HtmlPage page) throws LiteBotException{
        HtmlElement task = page.getBody();
        if (task == null) throw new LiteBotException(CANNOT_FIND_TASK_NUMBER);
        Matcher matcher = taskNumPattern.matcher(task.asText());
        String taskNum = "";
        if (matcher.find())
            taskNum = matcher.group();
        else
            throw new LiteBotException(CANNOT_FIND_TASK_NUMBER);
        return taskNum.replace("Задание ", "");
    }

    private String parseTime(HtmlPage page){
        DomElement task = page.getElementById("clock2");
        if (task == null)
            throw new LiteBotException("Не могу найти время на уровне");
        return "Время на уровне: "+task.getTextContent();
    }

    private String parseSysMessage(HtmlPage page){
        DomNode table = //(HtmlDivision)page.getBody().getFirstByXPath("//div[@class='title']");
        page.getBody().getFirstByXPath("//table");
        System.out.println(table.asXml());

        return table             //table div
                .getFirstChild() //<tbody>
                .getChildNodes() //<tr> list
                .get(1)
                .getChildNodes() //<td> list
                .get(1).getTextContent();

    }

    private HtmlPage inputCode(String code) throws IOException, ElementNotFoundException, LiteBotException {
        HtmlPage page = getGamePage();
        HtmlElement submit = null;
        List<HtmlForm> forms = page.getForms();
        int i = 0;
        while (i < forms.size()) {
            try {
                forms.get(i).getInputByName("cod").setValueAttribute(code);
                submit = forms.get(i).getElementsByAttribute("input", "type", "submit").get(0);
            } catch (ElementNotFoundException e) {
                i++;
                continue;
            }
            break;
        }
        if (submit!= null) {
            page = submit.click();
        } else {
            throw new LiteBotException(CANNOT_INPUT_CODE);
        }
        //return page;
        // TODO: 27.01.2017 del
        return getGamePage();
    }

    private HtmlPage getGamePage() throws IOException {
        return webClient.getPage(gameUrl);
    }

    private Code analyzeCodes(List<ArrayList<Code>> parsedSectors, String codeVal){
        ArrayList<Code> curSector;
        Code currentCode = null;
        Code parsedCode;
        boolean found = false;

        if (!sectors.isEmpty()){
            for(int sector = 0; sector < parsedSectors.size(); sector++){
                curSector = parsedSectors.get(sector);
                for(int code = 0; code < curSector.size(); code++){
                    currentCode = sectors.get(sector).get(code);
                    parsedCode = parsedSectors.get(sector).get(code);
                    if (parsedCode.getValue().length()>4 && !currentCode.getGotten()){
                        currentCode.setGotten(true);
                        currentCode.setCode(codeVal);
                        found = true;
                    }
                    if (found) break;
                }
                if (found) break;
            }
        }

        return found ? currentCode : null;
    }

    private List<ArrayList<Code>> parseCodePage(HtmlPage page){
        HtmlDivision task = (HtmlDivision)page.getBody().getFirstByXPath("//div[@class='dcodes']");
        if (task == null) throw new LiteBotException(CANNOT_LOAD_PAGE);
        String s = task.asXml().replace("\n","").replace("\r", "").replace(" ", "");
        System.out.println(s);
        Matcher matcher = sectorsPattern.matcher(s);
        List<ArrayList<Code>> parsedSectors = new ArrayList<ArrayList<Code>>();

        while (matcher.find()) {
            String codesStr = matcher.group();
            System.out.println("CODE STR:\n"+codesStr);
            codesStr = codesStr.replace("основныекоды:", "").replace("<br","");
            //System.out.println(codesStr);
            ArrayList<String> codes = new ArrayList<String>(Arrays.asList(codesStr.split(",")));
            ArrayList<Code> sector = new ArrayList<Code>();

            if (!codes.isEmpty()) {
                for (int i = 0; i < codes.size(); i++) {
                    /*if (!parsedSectors.isEmpty()) {
                        sector = parsedSectors.get(parsedSectors.size() - 1);
                    }*/
                    Code code = createCode(codes.get(i), parsedSectors.size()+1, sector.size()+1);
                    sector.add(code);
                }
            }
            parsedSectors.add(sector);
        }

        return parsedSectors;
    }

    private Code createCode(String value, Integer sector, Integer num){
        Matcher curCodeMatcher = codePattern.matcher(value);
        String level = null;
        if (curCodeMatcher.find()) level = curCodeMatcher.group();
        return new Code(value, level, sector, num);
    }

    public String getGameUrl() {
        return gameUrl;
    }

    public void setGameUrl(String gameUrl) {
        this.gameUrl = gameUrl;
    }
}
