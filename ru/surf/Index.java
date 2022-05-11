package ru.surf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;


public class Index {
    // Лучше использовать интерфейс (Map) при объявление переменной, а не его реализацию.
    // Сделал бы поля приватными.
    // Инициализацию мапы убрал бы из конструктора. Лучше в строке объявления инициализировать.
    // Лучше использовать потоко безопасную мапу. Например ConcurrentSkipListMap.
    TreeMap<String, List<Pointer>> invertedIndex;
    ExecutorService pool;

    public Index(ExecutorService pool) {
        this.pool = pool;
        invertedIndex = new TreeMap<>();
    }

    public void indexAllTxtInPath(String pathToDir) throws IOException {

        /* Лучше поменять название переменной на более понятное.
         * Например directoryPath или просто path.
         */
        // Можно добавить проверку на null аргумента pathToDir
        Path of = Path.of(pathToDir);

        /* Возможно стоило бы назвать переменную немного иначе.
         * Что бы было понятно что это очередь.
         * Например filesQueue
         * Вместимость очереди возможно стоит вынести в отдельную переменную.
         */
        BlockingQueue<Path> files = new ArrayBlockingQueue<>(2);

        /* С такой вместимостью очереди будет выкидываться exception при условии что в кол-во path будет больше чем 2.
         * Нужно либо написать обработку исключения, что делать при превышении вместимости очереди,
         * либо прописать класс Producer, который будет отвечать за наполнение очереди. Вынести в отдельный поток.
         */
        // У блока try отсутствует блок catch или final.
        try (Stream<Path> stream = Files.list(of)) {
            /* Возможно стоит добавить проверку на то, является ли файлом текущий path.
             * Если нужно индексировать не только файлы в текущей директории, но вовсех вложенных,
             * можно прописать рекурсивный проход по всем path. Что бы вытянуть в очередь все вложенные файлы.
             * Так же можно прописать проверку на то является ли файл текстовым.
             * Судя по названию метода обрабатываются только текстовые файлы.
             */
            stream.forEach(files::add);
        }

        // Возможно лучше использовать цикл вместо дублирования строк кода.
        pool.submit(new IndexTask(files));
        pool.submit(new IndexTask(files));
        pool.submit(new IndexTask(files));
    }

    // Опять же лучше использовать интерфейс Map вместо имплементации TreeMap.
    public TreeMap<String, List<Pointer>> getInvertedIndex() {
        return invertedIndex;
    }

    // GetRelevantDocuments -> getRelevantDocuments
    public List<Pointer> GetRelevantDocuments(String term) {
        return invertedIndex.get(term);
    }

    // Можно было бы переиспользовать метод getRelevantDocuments()
    public Optional<Pointer> getMostRelevantDocument(String term) {
        return invertedIndex.get(term).stream().max(Comparator.comparing(o -> o.count));
    }

    // Не уверен что понадобиться создавать класс Pointer отдельно от класса ru.surf.Index.
    // Так что статическим этот класс делать не обязательно.
    // Класс довольно простой. Можно его попробовать заменить на HashMap<String, Integer>.
    static class Pointer {
        private Integer count;
        private String filePath;

        public Pointer(Integer count, String filePath) {
            this.count = count;
            this.filePath = filePath;
        }

        // Дополнительно можно указать название класса
        @Override
        public String toString() {
            return "{" + "count=" + count + ", filePath='" + filePath + '\'' + '}';
        }
    }

    class IndexTask implements Runnable {

        private final BlockingQueue<Path> queue;

        public IndexTask(BlockingQueue<Path> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                // Название переменной лучше поменять на более подходящее. Например filePath.
                Path take = queue.take();
                List<String> strings = Files.readAllLines(take);

                // Тут мы работаем с общим ресурсом invertedIndex.
                // По этому как ранее указал лучше использовать потокобезопасную коллекцию.
                strings.stream()
                        // Возможно лучше использовать RegEx. Вместо пробела. Указать \\s вместо " ".
                        .flatMap(str -> Stream.of(str.split(" ")))
                        .forEach(word -> invertedIndex.compute(word, (k, v) -> {
                            if (v == null)
                                return List.of(new Pointer(1, take.toString()));
                            else {
                                // Опять же лучше использовать интерфейс List вместо имплементации ArrayList.
                                ArrayList<Pointer> pointers = new ArrayList<>();

                                if (v.stream().noneMatch(pointer -> pointer.filePath.equals(take.toString()))) {
                                    pointers.add(new Pointer(1, take.toString()));
                                }

                                // Бессмысленный повторный проход по списку Pointer'ов.
                                // Можно закинуть в else условие от предыдущего условия.
                                v.forEach(pointer -> {
                                    if (pointer.filePath.equals(take.toString())) {
                                        pointer.count = pointer.count + 1;
                                    }
                                });

                                pointers.addAll(v);

                                return pointers;
                            }

                        }));

            } catch (InterruptedException | IOException e) {
                /* Не очень информативный проброс исключения.
                 * Как вариант можно передать в качестве аргумента в новый exception
                 * либо сообщение, либо сам exception.
                 * Ну и логирования мне кажется тут очень не хватает.
                 */
                throw new RuntimeException();
            }
        }
    }
}