package JTranslation;

import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.*;

import JTranslation.DataHandling.Container;
import JTranslation.DataHandling.Emojis;
import JTranslation.DataHandling.JsonHandler;
import JTranslation.DataHandling.Languages;
import JTranslation.Exceptions.*;
import JTranslation.ValidityController.Regex;
import JTranslation.ValidityController.RegexSupervisor;


public class JTranslation {
    // New version of JTranslation i18n manager

    private final String root;
    private Map<String, Container> languagesContainers = null;

    @SneakyThrows
    protected JTranslation(String path) {
        ArrayList<String> processedRoute = new RegexSupervisor(Regex.PATH_DISASSEMBLER, path).matchingList();

        checkRouteValidity(processedRoute, path);

        this.root = processedRoute.get(0);
        String[] locales = new String[] { processedRoute.get(1) };

        load(locales);
    }

    @SneakyThrows
    protected JTranslation(String root, String[] locales) {
        this.root = root;

        load(locales);
    }

    // Load/Reload methods
    public void reload() {
        load(languagesContainers.keySet().toArray(new String[0]));
    }

    @SneakyThrows
    public void reload(@NonNull String... locales) {
        Set<String> newLocales = languagesContainers.keySet();

        for (String locale : locales) {
            if (!languagesContainers.containsKey(locale)) {
                newLocales.add(locale);
            }
        }

        load(newLocales.toArray(new String[0]));
    }

    @SneakyThrows
    public void removeLocale(@NonNull String... locales) {
        checkLocaleBound(locales.length);

        for (String locale : locales) {
            checkLocaleExistence(locale);
            languagesContainers.remove(locale);
        }
    }

    @SneakyThrows
    private void load(@NonNull String... locales) {
        if (languagesContainers == null) {
            languagesContainers = new LinkedHashMap<>();
        }

        for (String locale : locales) {
            languagesContainers.put(
                locale,
                new JsonHandler(root, locale).getContainerFromJson()
            );
        }

        checkJsonEquity();
        replaceEmojisIntoContainers();
    }

    @SneakyThrows
    public String getLang(String key, Object... args) {
        String locale = languagesContainers.entrySet().iterator().next().getKey();
        checkKeyExistence(locale, key);

        return replaceTextLabels(
            locale,
            key,
            args
        );
    }

    @SneakyThrows
    public String getLangWithLocale(String locale, String key, Object... args) {
        checkLocaleValidity(locale);
        checkKeyExistence(locale, key);

        return replaceTextLabels(
            locale,
            key,
            args
        );
    }

    // Replacers
    @SneakyThrows
    private void replaceEmojisIntoContainers() {
        RegexSupervisor regexSupervisor = new RegexSupervisor(Regex.EMOJI_FOUNDER);

        for (Map.Entry<String, Container> language : languagesContainers.entrySet()) {
            Map<String, String> languageChain = language.getValue().getContainer();

            for (String key : languageChain.keySet()) {
                languageChain.replace(
                    key,
                    replaceEmoji(regexSupervisor, languageChain.get(key), key, language.getKey())
                );
            }
        }
    }

    @SneakyThrows
    private String replaceEmoji(@NonNull RegexSupervisor supervisor, String text, String key, String locale) {
        Emojis emojis = Emojis.getInstance();
        supervisor.executeRegexOnString(text);
        ArrayList<String> matchesFound = supervisor.matchingList();

        if (matchesFound == null) return text;

        String editedText = text;
        for (String value : matchesFound) {
            editedText = editedText.replaceFirst(value,
                new StringBuilder()
                    .append("\\\\")
                    .append(emojis.getEmoji(value.replace(":", ""), key, locale))
                    .toString()
            );
        }

        return editedText;
    }

    @SneakyThrows
    private String replaceTextLabels(String locale, String key, @NonNull Object... args) {
        String text = languagesContainers.get(locale).getContainer().get(key);

        checkLabelRelationship(text, args.length, key, locale);

        for (Object obj : args) {
            text = text.replaceFirst(Regex.LABEL_FOUNDER.getRegex(), obj.toString());
        }
        return text;
    }

    // Checkers
    private void checkRouteValidity(ArrayList<String> processedRoute, String path) throws LocaleNotFoundException, BrokenRouteException {
        if (processedRoute == null) throw new BrokenRouteException(
            new StringBuilder()
                .append("Unable to extrapolate root folder and language from path: ")
                .append(path)
                .append("\nRecommended route: \\root\\locale.json")
                .toString()
        );

        checkLocaleValidity(processedRoute.get(1));
    }

    protected static void checkLocaleValidity(String locale) throws LocaleNotFoundException {
        if (!Languages.contains(locale)) throw new LocaleNotFoundException(
            new StringBuilder()
                .append("local code invalid or not found: ")
                .append(locale)
                .append("\nValid codes are available at LINK")
                .toString()
        );
    }

    private void checkKeyExistence(String locale, String key) throws KeyNotFoundException {
        if (!languagesContainers.get(locale).getContainer().containsKey(key)) throw new KeyNotFoundException(
                new StringBuilder()
                    .append("No key corresponding to \"")
                    .append(key)
                    .append("\" found on the loaded locale \"")
                    .append(locale)
                    .append("\"")
                    .toString()
        );
    }

    private void checkLabelRelationship(String text, int argsAmount, String key, String locale) throws ArgumentsOutOfRangeException {
        ArrayList<String> matches = new RegexSupervisor(Regex.LABEL_FOUNDER, text).matchingList();

        if (matches == null) return;

        if (matches.size() != argsAmount) throw new ArgumentsOutOfRangeException(
            new StringBuilder()
                .append("Amount of mismatched arguments in \"")
                .append(key).append("\" at the locale \"")
                .append(locale).append("\"\nArguments in the label: ")
                .append(matches.size()).append("\nArguments passed: ").append(argsAmount)
                .toString()
        );
    }

    private void checkLocaleExistence(String locale) throws LocaleNotAvailableInContainerException {
        if (!languagesContainers.containsKey(locale)) throw new LocaleNotAvailableInContainerException(
                new StringBuilder()
                    .append("Unable to remove the locale as it is not in the loaded containers: ")
                    .append(locale)
                    .toString()
        );
    }

    private void checkLocaleBound(int localeLength) throws LocaleOutOfBoundException {
        if (localeLength >= languagesContainers.size()) throw new LocaleOutOfBoundException(
            "You cannot remove more locales than are loaded"
        );
    }

    private void checkJsonEquity() throws UnequalJsonKeysException, GeneralUnknownException {
        // Method to check if all jsons have the same keys
        if (languagesContainers.isEmpty()) throw new GeneralUnknownException(
            "An unknown error was encountered during execution. Error code E01"
        );
        Container compassContainer = languagesContainers.values().stream().findFirst().get();

        for (Container container : languagesContainers.values()) {
            if (!compassContainer.getContainer().keySet().equals(container.getContainer().keySet())) {
                throw new UnequalJsonKeysException(
                    "The json keys are not the same in the loaded locales, check that all jsons have the same keys!"
                );
            }
        }
    }
}
