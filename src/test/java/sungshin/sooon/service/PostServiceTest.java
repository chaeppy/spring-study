package sungshin.sooon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import sungshin.sooon.domain.entity.Account;
import sungshin.sooon.domain.entity.Post;
import sungshin.sooon.domain.repository.PostLikeRepository;
import sungshin.sooon.domain.repository.PostRepository;
import sungshin.sooon.dto.PostRequestDto;
import sungshin.sooon.dto.PostResponseDto;
import sungshin.sooon.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private PostService postService;

    // @Autowired 우리는 이미 유닛테스트를 통해 postRepository 를 충분히 검증했다. (심지어 jpa 에서 자동완성으로 제공하는 메소드는 이미 충분히 검증되어있음) => Mockito 사용!
    @Mock
    private PostRepository postRepository;
    //private AutoCloseable autoCloseable; @ExtendWith(MockitoExtension.class)로 대체

    @Mock
    private PostLikeRepository postLikeRepository;

    @BeforeEach
    void setUp() {
        /* @ExtendWith(MockitoExtension.class)로 대체
        autoCloseable = MockitoAnnotations.openMocks(this); //이 클래스의 모든 Mock 초기화
        */
        postService = new PostService(postRepository, postLikeRepository);
    }

    /* @ExtendWith(MockitoExtension.class)로 대체
    @AfterEach
    void tearDown() throws Exception{
        autoCloseable.close(); //테스트 후에 리소스 close
    }
    */

    @Test
        //@Disabled: run 하지 않겠다는 뜻
    void findAllByAccount() {
        //given
        Account account = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();

        //when
        postService.findAllByAccount(account);

        //then
        verify(postRepository).findAllByAccountOrderByCreatedAtDesc(account);
    }

    @Test
    void findAll() {
        //when
        postService.findAll();

        //then
        verify(postRepository).findAll(); //deleteAll() 하면 Wanted but not invoked 하면서 test 실패
        /*
        우리는 service를 테스트할 때 레포지토리를 테스트하길 원하지않음. 왜냐하면 포스트레포지토리가 제대로 작동한다는 사실을 알고있기 때문. => Mock(가짜)를 사용한다.
        이제 우리가 얻는 이점은 빠르다는 것이다. 데이터베이스를 만들 필요도 없고, 삽입할 필요도없다.
         */
    }

    @Test
    void findById_ifPostExists() {
        //given
        Long id = 2L;
        Account account = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();
        Post post = Post.builder().id(id).title("포스트 테스트").content("포스트 테스트 입니다").isAnonymous(false).build();
        post.setAccount(account);
        PostResponseDto wantedDto = PostResponseDto.of(post);
        given(postRepository.findById(id)).willReturn(Optional.of(post));

        //when
        PostResponseDto resultDto = postService.findById(id);

        //then
        verify(postRepository).findById(id);
        assertThat(resultDto).isEqualTo(wantedDto);
    }

    @Test
    void save() {
        //given
        Account account = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();
        PostRequestDto postRequestDto = PostRequestDto.builder().title("포스트 테스트").content("포스트 테스트 입니다").isAnonymous(false).build();
        Post post = postRequestDto.toPost();
        post.setAccount(account);

        given(postRepository.save(post)).willReturn(post); //실제 레포지토리를 쓰는 것이 아니기때문에 얘가 return하는 것을 지정해줘야함.

        //when
        postService.save(account, postRequestDto);

        //then
        ArgumentCaptor<Post> postArgumentCaptor = ArgumentCaptor.forClass(Post.class); //내가 save하길 원한 객체랑 실제 service에서 save할 때의 아규먼트인 객체랑 비교해서 원하는 값 그대로 save하는지 테스트.
        verify(postRepository).save(postArgumentCaptor.capture()); //레포지토리.save할때 들어와있는 파라미터를 캡쳐한다. 원래는 getId가 무슨 값을 반환하는지 까지 테스트해야되지만 그건 jpa의 영역임.. 내가 id를 세팅할 순 없다. 다만 save할 때 똑같은 객체가 들어가는지만 검증한다.
        Post capturedPost = postArgumentCaptor.getValue(); //캡쳐한 밸류를 반환받음
        assertThat(capturedPost).isEqualTo(post); //객체 비교를 위해서 @EqualsAndHashcode 필요
    }

    @Test
    void delete_ifAccountIdMatchesAndPostExists() {
        //given
        Long id = 2L;
        Account account = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();

        Post post = Post.builder().id(id).title("포스트 테스트").content("포스트 테스트 입니다").isAnonymous(false).build();
        post.setAccount(account);
        given(postRepository.findById(id)).willReturn(Optional.of(post));

        //when
        postService.delete(account, id);

        //then
        ArgumentCaptor<Post> postArgumentCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).delete(postArgumentCaptor.capture());
        Post capturedPost = postArgumentCaptor.getValue(); //캡쳐한 밸류를 반환받음
        assertThat(capturedPost).isEqualTo(post);
    }

    @Test
    void delete_ifAccountIdDoesNotMatches() {
        //given
        Long id = 2L;
        Account account1 = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();

        Post post = Post.builder().id(id).title("포스트 테스트").content("포스트 테스트 입니다").isAnonymous(false).build();
        Account account2 = Account.builder().id(2L).email("chaeppy2@sswu.community").password("tempPassword123").nickname("채피2").registeredDateTime(LocalDateTime.now()).build();
        post.setAccount(account2);

        given(postRepository.findById(id)).willReturn(Optional.ofNullable(post));

        //when
        //then
        assertThatThrownBy(() -> postService.delete(account1, id)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("삭제 권한이 없습니다.");
        verify(postRepository, never()).delete(any());
    }

    @Test
    void delete_ifPostDoesNotExists() {
        //given
        Long id = 2L;
        given(postRepository.findById(id)).willReturn(Optional.ofNullable(null));
        Account account = Account.builder().id(1L).email("chaeppy@sswu.community").password("tempPassword123").nickname("채피").registeredDateTime(LocalDateTime.now()).build();

        //when
        //then
        assertThatThrownBy(() -> postService.delete(account, id)).isInstanceOf(NotFoundException.class).hasMessageContaining("게시글이 존재하지 않습니다.");
        verify(postRepository, never()).delete(any());
    }

}