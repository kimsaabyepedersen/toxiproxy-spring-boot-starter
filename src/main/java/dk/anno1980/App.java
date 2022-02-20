package dk.anno1980;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@SpringBootApplication
@RestController
public class App {
  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  @GetMapping
  Time now() {
    return new Time(LocalDateTime.now().toString());
  }

  static class Time {
    private String time;

    public Time(String time) {
      this.time = time;
    }

    public String getTime() {
      return time;
    }
  }
}
