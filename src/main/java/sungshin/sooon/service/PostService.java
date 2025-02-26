package sungshin.sooon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sungshin.sooon.domain.entity.Account;
import sungshin.sooon.domain.entity.Post;
import sungshin.sooon.domain.entity.PostLike;
import sungshin.sooon.domain.repository.PostLikeRepository;
import sungshin.sooon.domain.repository.PostRepository;
import sungshin.sooon.dto.PagingPostResponseDto;
import sungshin.sooon.dto.PostRequestDto;
import sungshin.sooon.dto.PostResponseDto;
import sungshin.sooon.exception.AlreadyExistsException;
import sungshin.sooon.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    /*
       바로 (readOnly=true)인데 이 옵션을 추가해주면 트랜잭션 범위는 유지하되, 조회 기능만 남겨두어 조회 속도가 개선되기 때문에 등록, 수정, 삭제 기능이 없는 서비스 메소드에 사용하는 것이 좋다.
   */
    @Transactional(readOnly = true)
    public List<PostResponseDto> findAllByAccount(Account account) {
        return postRepository
                .findAllByAccountOrderByCreatedAtDesc(account)
                .stream()
                .map(PostResponseDto::of)
                .collect(Collectors.toList());
                /*
                    스트림은 '데이터의 흐름’입니다. 배열 또는 컬렉션 인스턴스에 함수 여러 개를 조합해서 원하는 결과를 필터링하고 가공된 결과를 얻을 수 있습니다
                     또 하나의 장점은 간단하게 병렬처리(multi-threading)가 가능하다는 점입니다. 하나의 작업을 둘 이상의 작업으로 잘게 나눠서 동시에 진행하는 것을 병렬 처리(parallel processing)라고 합니다. 즉 쓰레드를 이용해 많은 요소들을 빠르게 처리할 수 있습니다.
                     생성-> 가공(필터링(filtering) 및 맵핑(mapping) 등 원하는 결과를 만들어가는 중간 작업) -> 결과 (collect 메소드는 또 다른 종료 작업)
                     출처: https://futurecreator.github.io/2018/08/26/java-8-streams/
                 */
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> findAll() {
        return postRepository
                .findAll()
                .stream()
                .map(PostResponseDto::of)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagingPostResponseDto findAllOrderBy(Integer page, Integer size, String order) {
        PageRequest pageRequest;
        if (order.equals("likeCount")){
            pageRequest = PageRequest.of(page, size, Sort.by("likeCount").descending());
            //여기서 sort에 넘겨줘야하는 정보는 도메인 클래스의 Property 혹은 alais 여야 합니다.
            //@Fomula 수식에 작성하는 모든 것이 ORDER BY 절에 전달되므로 새 열을 정렬 순서로 사용할 수 있습니다.
            // https://stackoverflow.com/questions/37139985/spring-data-jpa-is-it-possible-to-sort-on-a-calculated-property
        } else if (order.equals("createdAt")){
            pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        } else {
            throw new NotFoundException("존재하지 않는 정렬 값입니다.");
        }

        Slice<Post> posts = postRepository.findAll(pageRequest); //slice는 total count쿼리를 날리지 않기때문에 성능이 더 낫다. 그러나 findAll의 Return값은 Page

        List<PostResponseDto> postResponseDto = posts.stream()
                .map(PostResponseDto::of)
                .collect(Collectors.toList());

        return PagingPostResponseDto.builder()
                .currentPage(posts.getNumber())
                .currentSize(posts.getNumberOfElements())
                .hasNextPage(posts.hasNext())
                .posts(postResponseDto).build();
    }

    @Transactional(readOnly = true)
    public PostResponseDto findById(Long id) {
        Post post = findByIdOrThrowNotFoundException(id);
        return PostResponseDto.of(post);
    }


    @Transactional
    public PostResponseDto save(Account account, PostRequestDto postRequestDto) {
        Post post = postRequestDto.toPost();
        post.setAccount(account);
        return PostResponseDto.of(postRepository.save(post));
    }

    @Transactional
    public void delete(Account account, Long id) {
        Post post = findByIdOrThrowNotFoundException(id);

        //현재사용자와 게시글을 작성한 유저의 아이디를 비교해서 권한이있는지 확인해야함. 인터셉터로 해야하나?? 서비스단 말고 더 좋은 위치는 없나?
        if (post.getAccount().getId() != account.getId()) {
            throw new AccessDeniedException("삭제 권한이 없습니다.");
        }

        postRepository.delete(post);
    }

    public Post findByIdOrThrowNotFoundException(Long id) {
        Post post = postRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("게시글이 존재하지 않습니다."));
        return post;
    }

    @Transactional
    public PostResponseDto update(Account account, Long id, PostRequestDto postRequestDto) {
        Post post = findByIdOrThrowNotFoundException(id);

        //현재사용자와 게시글을 작성한 유저의 아이디를 비교해서 권한이있는지 확인해야함. 인터셉터로 해야하나?? 서비스단 말고 더 좋은 위치는 없나?
        if (post.getAccount().getId() != account.getId()) {
            throw new AccessDeniedException("수정 권한이 없습니다.");
        }

        postRequestDto.apply(post);
        return PostResponseDto.of(post);
    }

    @Transactional
    public void saveLike(Account account, long postId) {
        Post post = findByIdOrThrowNotFoundException(postId);

        if (postLikeRepository.findByAccountAndPost(account, post) != null) {
            throw new AlreadyExistsException("이미 좋아요한 게시글입니다.");
        }

        PostLike postLike = PostLike.builder().post(post).account(account).build();
        postLikeRepository.save(postLike);
    }

    @Transactional
    public void deleteLike(Account account, long postId, long userId) {
        if (account.getId() != userId) {
            throw new AccessDeniedException("삭제 권한이 없습니다.");
        }

        Post post = findByIdOrThrowNotFoundException(postId);
        PostLike postLike = postLikeRepository.findByAccountAndPost(account, post);

        if (postLike == null) {
            throw new NotFoundException("좋아요한 기록이 없습니다.");
        }

        postLikeRepository.delete(postLike);
    }

    @Transactional
    public void findLikeByUserId(Account account, long postId, long userId) {
        if (account.getId() != userId) {
            throw new AccessDeniedException("조회 권한이 없습니다.");
        }

        Post post = findByIdOrThrowNotFoundException(postId);

        PostLike postLike = postLikeRepository.findByAccountAndPost(account, post);

        if (postLike == null) {
            throw new NotFoundException("좋아요한 기록이 없습니다.");
        }
    }
}